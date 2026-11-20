package com.a207.runkk.app.core.designsystem

import kotlin.math.abs

/**
 * 트랙 상대가 보이는 자리(거리 차 상대 − 나, m)와 불투명도(S15P31A207-335 4차). 받은 격차 사이는 [GapMotion]이 매 프레임 잇고, 이 클래스는
 * 언제·어디서 나타나고 사라지는지와 보이는 동안의 한 프레임 이동 상한을 정한다. 시각은 초 단위로 프레임 시계와 같은 것을 쓴다.
 *
 * - 나타나기: 몸이 준비됐고 실제 자리가 트랙 안일 때만 그린다. 처음 나타날 때 실제 자리가 내 자리이거나 뒤면([NEAR_METERS] 이하) 출발 쪽
 *   트랙 밖([ENTRY_METERS])에서 [ENTRY_SECONDS] 동안 감속하며 들어온다. 앞이면 그 자리에서 [FADE_SECONDS] 동안 서서히 나타난다: 앞에 있는
 *   상대를 왼쪽에서 끌고 오거나 오른쪽 끝에서 왼쪽으로 끌어오지 않아 등장 중 내 자리를 가로지르지 않는다. 트랙 밖에서 돌아오거나 격차를
 *   다시 알게 된 때도 그 자리에서 서서히 나타난다(실제 자리 쪽 가장자리 근처다).
 * - 사라지기: 실제 자리가 트랙 밖(가장자리 칩)이거나 몸이 준비 중이면 [FADE_SECONDS] 동안 사라지고 다시 그리지 않는다.
 * - 큰 변화: 받은 값이 따라가던 값에서 [JUMP_METERS]보다 멀면(상대 속도 상한 4 m/s로 1초에 갈 수 없는 변화) 보이는 채로 옮기지 않는다.
 *   보이고 있으면 그 자리에서 사라졌다가 새 자리에서 나타나고, 안 보이면 새 자리로 바로 옮긴다.
 * - 상한: 보이는 동안 한 프레임 이동은 [MAX_STEP_METERS](트랙 폭의 2.5%)를 넘지 않는다. 넘을 만큼 멀면 다음 프레임들에 나눠 따라간다.
 */
internal class TrackOtherMotion {
    private val gap = GapMotion()

    /** 보이는 자리(거리 차, m). */
    var meters = ENTRY_METERS
        private set

    /** 불투명도(0 = 그리지 않음, 1 = 다 보임). */
    var alpha = 0f
        private set

    private var follow = 0.0
    private var lastAhead: Double? = null
    private var pendingJump: Double? = null
    private var slideStartAt = Double.NaN
    private var lastFrameAt = Double.NaN
    private var hasAppeared = false
    private var isWanted = false

    /** [atSeconds]에 받은 거리 차 [ahead]. 직전과 같은 값은 무시한다. */
    fun receive(ahead: Double, atSeconds: Double) {
        if (ahead == lastAhead) return
        lastAhead = ahead
        when {
            !gap.hasValue -> {
                gap.snap(ahead, atSeconds)
                follow = ahead
            }

            pendingJump != null -> pendingJump = ahead

            abs(ahead - follow) > JUMP_METERS && alpha > 0f -> {
                pendingJump = ahead
                slideStartAt = Double.NaN
            }

            abs(ahead - follow) > JUMP_METERS -> {
                gap.snap(ahead, atSeconds)
                follow = ahead
            }

            else -> gap.receive(ahead, atSeconds)
        }
    }

    /** 격차를 모르게 됐다. 바로 숨기고, 다시 알게 되면 그 자리에서 서서히 나타난다. */
    fun forget() {
        gap.reset()
        lastAhead = null
        pendingJump = null
        slideStartAt = Double.NaN
        alpha = 0f
    }

    /** [atSeconds] 프레임의 [meters]와 [alpha]를 정한다. [isReady]는 몸 준비, [isOnTrack]은 실제 자리가 트랙 안인지다. */
    fun frame(atSeconds: Double, isReady: Boolean, isOnTrack: Boolean) {
        val elapsed = if (lastFrameAt.isNaN()) 0.0 else (atSeconds - lastFrameAt).coerceIn(0.0, MAX_FRAME_GAP)
        lastFrameAt = atSeconds
        if (!gap.hasValue) {
            alpha = 0f
            isWanted = false
            return
        }
        follow = gap.frame(atSeconds)
        val fade = (elapsed / FADE_SECONDS).toFloat()
        pendingJump?.let { jump ->
            // 큰 변화: 그 자리에 선 채 사라진 뒤 새 자리로 옮긴다.
            alpha = (alpha - fade).coerceAtLeast(0f)
            if (alpha > 0f) return
            // 다 사라진 이 프레임에 옮기고, 새 자리에서 나타나기는 다음 프레임부터 한다(두 자리에 걸친 보이는 프레임이 없다).
            gap.snap(jump, atSeconds)
            follow = jump
            meters = jump
            pendingJump = null
            return
        }
        isWanted = isReady && isOnTrack
        if (alpha == 0f) {
            meters = follow
            if (!isWanted) return
            if (!hasAppeared && follow <= NEAR_METERS) {
                slideStartAt = atSeconds
                meters = ENTRY_METERS
                alpha = 1f
            }
            hasAppeared = true
        }
        if (!slideStartAt.isNaN() && isWanted) {
            val progress = ((atSeconds - slideStartAt) / ENTRY_SECONDS).coerceIn(0.0, 1.0)
            // 감속(ease-out quad): 빠르게 들어와 제자리에서 멈춘다. 끝에서 따라가던 값과 같아진다.
            val eased = 1 - (1 - progress) * (1 - progress)
            meters = stepToward(ENTRY_METERS + (follow - ENTRY_METERS) * eased)
            if (progress >= 1.0) slideStartAt = Double.NaN
            return
        }
        slideStartAt = Double.NaN
        alpha = if (isWanted) (alpha + fade).coerceAtMost(1f) else (alpha - fade).coerceAtLeast(0f)
        meters = if (alpha > 0f) stepToward(follow) else follow
    }

    /** 더 그릴 것이 없다(불투명도·자리가 다 닿았고 [GapMotion]도 멈췄다). */
    fun isSettled(atSeconds: Double): Boolean = !gap.hasValue ||
        (
            pendingJump == null &&
                slideStartAt.isNaN() &&
                alpha == (if (isWanted) 1f else 0f) &&
                abs(meters - follow) < SETTLED_METERS &&
                gap.isSettled(atSeconds)
            )

    private fun stepToward(target: Double) = meters + (target - meters).coerceIn(-MAX_STEP_METERS, MAX_STEP_METERS)

    private companion object {
        /** 출발 쪽 트랙 밖 자리: 폭 −0.2(내 자리 0.4에서 폭 0.6 = 18 m 뒤). 상대 칸(폭의 약 28%)이 카드 왼쪽 밖에 다 숨는다. */
        const val ENTRY_METERS = -18.0

        /** 처음 나타날 때 이보다 앞이면 그 자리에서 나타난다(정수 m 반올림 반 칸까지는 내 자리로 본다). */
        const val NEAR_METERS = 0.5
        const val ENTRY_SECONDS = 0.9
        const val FADE_SECONDS = 0.2

        /** 상대 속도 상한(4 m/s)으로 값 한 번(약 1초) 사이에 갈 수 있는 거리 + 정수 반올림 0.5 m. */
        const val JUMP_METERS = 4.5

        /** 보이는 동안 한 프레임 이동 상한: 트랙 폭(30 m)의 2.5%. */
        const val MAX_STEP_METERS = 0.75
        const val MAX_FRAME_GAP = 0.034
        const val SETTLED_METERS = 0.001
    }
}
