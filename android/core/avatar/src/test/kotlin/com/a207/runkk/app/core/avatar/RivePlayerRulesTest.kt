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
        assertEquals(View.GONE, RivePlayerRules.visibility(AvatarMotion.RUN, AvatarMotion.STAND, applied))
        assertEquals(View.VISIBLE, RivePlayerRules.visibility(AvatarMotion.STAND, AvatarMotion.STAND, applied))
        assertEquals(View.INVISIBLE, RivePlayerRules.visibility(AvatarMotion.RUN, AvatarMotion.RUN, applied))
    }

    @Test
    fun `자세가 바뀌어 새 동작 텍스처를 기다리는 동안 앞 동작을 그대로 보여 몸이 비지 않는다(S15P31A207-335)`() {
        AvatarMotion.entries.forEach { current ->
            val previous = AvatarMotion.entries.single { it != current }
            val applied = setOf(previous)
            assertEquals(View.INVISIBLE, RivePlayerRules.visibility(current, current, applied))
            assertEquals(View.VISIBLE, RivePlayerRules.visibility(previous, current, applied), "$previous → $current")
        }
        // 둘 다 준비되면 지금 동작만 보인다(겹쳐 그리지 않는다).
        val both = AvatarMotion.entries.toSet()
        assertEquals(View.VISIBLE, RivePlayerRules.visibility(AvatarMotion.RUN, AvatarMotion.RUN, both))
        assertEquals(View.GONE, RivePlayerRules.visibility(AvatarMotion.STAND, AvatarMotion.RUN, both))
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
