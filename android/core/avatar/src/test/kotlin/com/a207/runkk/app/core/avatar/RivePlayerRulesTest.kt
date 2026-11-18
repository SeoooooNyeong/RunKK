package com.a207.runkk.app.core.avatar

import android.view.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 뷰 규칙: 좌우 뒤집기는 달리기 플레이어만 `scaleX = -1`(D-762, D-765), 서기는 늘 1. 플레이어는 그 동작의 첫 텍스처가
 * 들어간 뒤에야 보이고(기본 그림 숨김), 보이지 않다가 보이게 된 때가 `onReady`다(D-737).
 */
class RivePlayerRulesTest {
    @Test
    fun `뒤집기는 달리기 플레이어 scaleX만 -1로 하고 서기는 1이다`() {
        assertEquals(-1f, RivePlayerRules.scaleX(AvatarMotion.RUN, mirrored = true))
        assertEquals(1f, RivePlayerRules.scaleX(AvatarMotion.RUN, mirrored = false))
        assertEquals(1f, RivePlayerRules.scaleX(AvatarMotion.STAND, mirrored = true))
        assertEquals(1f, RivePlayerRules.scaleX(AvatarMotion.STAND, mirrored = false))
    }

    @Test
    fun `지금 동작이 아니면 GONE, 텍스처 전이면 INVISIBLE, 텍스처가 들어가면 VISIBLE이다`() {
        val applied = setOf(AvatarMotion.STAND)
        assertEquals(View.GONE, RivePlayerRules.visibility(AvatarMotion.RUN, AvatarMotion.STAND, applied, null))
        assertEquals(View.VISIBLE, RivePlayerRules.visibility(AvatarMotion.STAND, AvatarMotion.STAND, applied, null))
        assertEquals(View.INVISIBLE, RivePlayerRules.visibility(AvatarMotion.RUN, AvatarMotion.RUN, applied, null))
    }

    @Test
    fun `자세가 바뀌어 새 동작 텍스처를 기다리는 동안 앞 동작을 그대로 보여 몸이 비지 않는다(S15P31A207-335)`() {
        AvatarMotion.entries.forEach { current ->
            val previous = AvatarMotion.entries.single { it != current }
            val applied = setOf(previous)
            assertEquals(View.INVISIBLE, RivePlayerRules.visibility(current, current, applied, previous))
            assertEquals(
                View.VISIBLE,
                RivePlayerRules.visibility(previous, current, applied, previous),
                "$previous → $current"
            )
        }
        // 둘 다 준비되고 지금 동작이 그려졌으면 지금 동작만 보인다(겹쳐 그리지 않는다).
        val both = AvatarMotion.entries.toSet()
        assertEquals(
            View.VISIBLE,
            RivePlayerRules.visibility(AvatarMotion.RUN, AvatarMotion.RUN, both, AvatarMotion.RUN)
        )
        assertEquals(
            View.GONE,
            RivePlayerRules.visibility(AvatarMotion.STAND, AvatarMotion.RUN, both, AvatarMotion.RUN)
        )
    }

    @Test
    fun `두 동작이 다 준비된 트랙 칸에서 자세가 바뀌면 새 플레이어가 첫 판을 그릴 때까지 앞 동작을 보여 몸이 사라지는 판이 없다(S15P31A207-335 3차)`() {
        // 숨어 있던(GONE) 플레이어는 보이게 된 뒤 화면 면을 만들고 첫 판을 그리기까지 몇 판이 걸린다(에뮬레이터 0.66초 ≈ 40판).
        val players = TwoPlayers(firstFrameAfter = 40)
        players.runUntilDrawn(AvatarMotion.RUN)
        listOf(AvatarMotion.STAND, AvatarMotion.RUN, AvatarMotion.STAND).forEach { next ->
            val bodyless = players.switchTo(next, frames = 60)
            assertEquals(emptyList(), bodyless, "→ $next: 몸이 그려진 플레이어가 하나도 보이지 않는 판")
            assertEquals(next, players.onScreen, "→ $next: 새 플레이어가 첫 판을 그렸다")
            assertEquals(
                listOf(next),
                players.visible(),
                "→ $next: 새 동작이 그려진 뒤에는 앞 동작을 숨긴다(겹쳐 남지 않는다)"
            )
        }
    }

    /**
     * [RiveAvatarView]의 서기·달리기 플레이어 둘을 판 단위로 흉내 낸다. 두 동작 텍스처는 이미 들어가 있다(트랙 칸 warmAllMotions).
     * 보이게 된 플레이어는 [firstFrameAfter]판 뒤부터 그린다. 지금 동작 플레이어가 판을 그리면 그 동작이 화면에 있는 동작([onScreen])이
     * 된다(첫 판 알림 → 보이기 다시 계산).
     */
    private class TwoPlayers(private val firstFrameAfter: Int) {
        private val applied = AvatarMotion.entries.toSet()
        private val visibleFrames = AvatarMotion.entries.associateWith { 0 }.toMutableMap()
        private var current = AvatarMotion.RUN
        var onScreen: AvatarMotion? = null
            private set

        fun visible(): List<AvatarMotion> =
            AvatarMotion.entries.filter { RivePlayerRules.visibility(it, current, applied, onScreen) == View.VISIBLE }

        /** 한 판. 몸이 그려진 플레이어가 하나라도 보이면 true. */
        fun frame(): Boolean {
            val shown = visible()
            AvatarMotion.entries.forEach { visibleFrames[it] = if (it in shown) visibleFrames.getValue(it) + 1 else 0 }
            val drawn = shown.filter { visibleFrames.getValue(it) > firstFrameAfter }
            if (current in drawn) onScreen = current
            return drawn.isNotEmpty()
        }

        fun runUntilDrawn(motion: AvatarMotion) {
            current = motion
            repeat(firstFrameAfter + 2) { frame() }
            check(onScreen == motion)
        }

        /** 자세를 [next]로 바꾸고 [frames]판을 돌린다. 몸이 보이지 않은 판 번호를 돌려준다. */
        fun switchTo(next: AvatarMotion, frames: Int): List<Int> {
            current = next
            return (1..frames).filterNot { frame() }
        }
    }

    @Test
    fun `트랙 칸은 서기·달리기 플레이어를 처음부터 함께 만들고 그 밖은 지금 동작만 만든다(S15P31A207-335)`() {
        assertEquals(AvatarMotion.entries.toSet(), RivePlayerRules.motionsToCreate(AvatarMotion.STAND, warmAll = true))
        assertEquals(setOf(AvatarMotion.RUN), RivePlayerRules.motionsToCreate(AvatarMotion.RUN, warmAll = false))
    }

    @Test
    fun `준비 알림은 보이게 된 뒤 첫 그림이 실제로 그려질 때 한 번이다(S15P31A207-335 2차)`() {
        var count = 0
        val notice = FirstFrameNotice { count++ }
        notice.frameDrawn()
        assertEquals(0, count, "보이기 전에 그린 판은 알리지 않는다")

        notice.shown()
        assertEquals(0, count, "보이게 된 것만으로는 아직 몸이 그려지지 않았다")
        notice.frameDrawn()
        assertEquals(1, count)
        notice.frameDrawn()
        notice.timedOut()
        assertEquals(1, count, "한 번 보인 뒤에는 다시 알리지 않는다")

        notice.shown()
        notice.timedOut()
        assertEquals(2, count, "그림이 오지 않아도 기다림이 끝나면 알린다(숨은 채 남지 않는다)")

        notice.shown()
        notice.cancel()
        notice.frameDrawn()
        assertEquals(2, count, "숨거나 정체가 바뀌면 기다리던 알림을 버린다")
    }

    @Test
    fun `보이지 않다가 보이게 된 때만 준비 알림이다`() {
        assertTrue(RivePlayerRules.becameVisible(View.INVISIBLE, View.VISIBLE))
        assertTrue(RivePlayerRules.becameVisible(View.GONE, View.VISIBLE))
        assertFalse(RivePlayerRules.becameVisible(View.VISIBLE, View.VISIBLE), "이미 보이면 다시 알리지 않는다")
        assertFalse(RivePlayerRules.becameVisible(View.VISIBLE, View.GONE))
        assertFalse(RivePlayerRules.becameVisible(View.INVISIBLE, View.INVISIBLE))
    }
}
