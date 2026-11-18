package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 트랙 보기 위치 계산([임시:D-성-257], [임시:D-성-258], [임시:D-성-306]). 거리 차를 화면 가로 위치(0 = 왼쪽 끝, 1 = 오른쪽 끝)와 가장자리
 * 칩, 결승선으로 바꾸는 순수 함수다. 화면 폭은 실제 거리 [WINDOW_METERS], 내 자리는 폭의 [MY_X]다.
 */
object TrackLayout {
    /** 트랙 화면 폭이 나타내는 실제 거리(m, 결정 U-4). */
    const val WINDOW_METERS = 30.0

    /** 내 가로 위치(폭의 40%). */
    const val MY_X = 0.4f

    /** 상대를 트랙 위에 그리는 가장자리 여백. 이 안쪽([EDGE_MARGIN]..1 − [EDGE_MARGIN])에 들어올 때만 아바타를 그린다. */
    const val EDGE_MARGIN = 0.08f

    /**
     * 계산 결과. [other]가 null이면 상대를 그리지 않고(격차 모름), [finish]가 null이면 결승선을 그리지 않는다. [otherRawX]는 자르지 않은
     * 상대 가로 위치다(가장자리 칩일 때도 있다). 트랙 카드는 이 값으로 상대를 카드 밖에서부터 그려 오는 쪽 가장자리에서 들어오게 한다
     * (S15P31A207-335).
     */
    data class Result(val myX: Float, val other: Other?, val finish: Finish?, val otherRawX: Float? = null)

    /** 상대 자리. */
    sealed interface Other {
        /** 트랙 위 가로 위치 [x]에 아바타를 그린다. */
        data class OnTrack(val x: Float) : Other

        /** 화면 밖이다: [isAhead]면 오른쪽, 아니면 왼쪽 가장자리 칩이고 [meters]는 차이(절댓값, m)다. */
        data class Edge(val isAhead: Boolean, val meters: Int) : Other
    }

    /** 결승선의 가로 위치. */
    data class Finish(val x: Float)

    /**
     * [aheadMeters]는 상대 − 나(m, 상대가 앞이면 양수)이고 모르면 null이다. [remainingMeters]는 목표까지 남은 거리이고 목표가 없으면
     * null이다. 결승선은 화면 안(`MY_X + r/30 ≤ 1`, 남은 약 18 m부터)에 들어올 때만 있고, 다 달렸으면 내 자리다.
     */
    fun of(aheadMeters: Double?, remainingMeters: Double?): Result = Result(
        myX = MY_X,
        other = aheadMeters?.let(::otherOf),
        finish = remainingMeters?.let(::finishOf),
        otherRawX = aheadMeters?.let(::xOf)
    )

    private fun otherOf(ahead: Double): Other {
        val x = xOf(ahead)
        return if (x in EDGE_MARGIN..(1f - EDGE_MARGIN)) {
            Other.OnTrack(x)
        } else {
            Other.Edge(isAhead = ahead > 0, meters = abs(ahead).roundToInt())
        }
    }

    private fun finishOf(remaining: Double): Finish? {
        if (remaining <= 0) return Finish(MY_X)
        val x = xOf(remaining)
        return if (x <= 1f) Finish(x) else null
    }

    /** 소수 넷째 자리에서 맞춘다(0.4 + 15/30이 0.9000001이 되어 경계에서 흔들리지 않게). */
    private fun xOf(meters: Double): Float =
        ((MY_X + meters / WINDOW_METERS) * X_SCALE).roundToInt() / X_SCALE.toFloat()

    private const val X_SCALE = 10_000
}
