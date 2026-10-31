package com.a207.runkk.app.data.push

import com.a207.runkk.app.domain.notification.GatedFeature
import com.a207.runkk.app.domain.notification.ReleaseLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1차 배포 잠금 빌드의 시스템 푸시(D-339, 코드 리뷰 U10-A2): 닫힌 기능의 알림은 띄우지 않는다. */
class PushReleaseFilterTest {
    private val locked = ReleaseLock { true }
    private val open = ReleaseLock { false }

    @Test
    fun `잠금 빌드에서 아바타·크루 알림은 띄우지 않고 그 밖은 띄운다`() {
        assertFalse(isReleased("BODY_SHAPE_CHANGED", locked))
        assertFalse(isReleased("BACKGROUND_UNLOCKED", locked))
        assertFalse(isReleased("CREW_CHAT", locked))
        assertFalse(isReleased("JOIN_REQUESTED", locked))
        assertTrue(isReleased("OPPONENT_ARRIVED", locked))
        assertTrue(isReleased("PLAN_MISSED", locked))
        assertTrue(isReleased(null, locked), "종류가 없으면 막지 않는다")
    }

    @Test
    fun `잠금이 아니면 모든 알림을 띄운다`() {
        listOf("BODY_SHAPE_CHANGED", "CREW_CHAT", "OPPONENT_ARRIVED", null).forEach {
            assertTrue(isReleased(it, open), "$it")
        }
    }

    @Test
    fun `종류 분류는 아바타 둘과 크루 열여섯이다`() {
        assertEquals(GatedFeature.AVATAR_CUSTOMIZE, GatedFeature.of("BACKGROUND_UNLOCKED"))
        assertEquals(GatedFeature.CREW, GatedFeature.of("REPORT_RESOLVED"))
        assertNull(GatedFeature.of("BATTLE_CORRECTED"))
    }
}
