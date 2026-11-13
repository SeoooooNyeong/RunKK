package com.a207.runkk.app.data.push

import com.a207.runkk.app.domain.notification.GatedFeature
import com.a207.runkk.app.domain.notification.ReleaseLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 미공개 기능 잠금 빌드의 시스템 푸시(D-339, 코드 리뷰 U10-A2): 닫힌 기능의 알림만 띄우지 않는다. */
class PushReleaseFilterTest {
    private val allClosed = ReleaseLock { false }
    private val crewOnly = ReleaseLock { it == GatedFeature.CREW }
    private val allOpen = ReleaseLock { true }

    @Test
    fun `닫힌 기능의 알림은 띄우지 않고 기능이 없는 알림은 띄운다`() {
        assertFalse(isReleased("BODY_SHAPE_CHANGED", allClosed))
        assertFalse(isReleased("BACKGROUND_UNLOCKED", allClosed))
        assertFalse(isReleased("CREW_CHAT", allClosed))
        assertFalse(isReleased("JOIN_REQUESTED", allClosed))
        assertTrue(isReleased("OPPONENT_ARRIVED", allClosed))
        assertTrue(isReleased("PLAN_MISSED", allClosed))
        assertTrue(isReleased(null, allClosed), "종류가 없으면 막지 않는다")
    }

    @Test
    fun `기능마다 따로 연다`() {
        assertTrue(isReleased("CREW_CHAT", crewOnly))
        assertFalse(isReleased("BACKGROUND_UNLOCKED", crewOnly))
    }

    @Test
    fun `모두 열리면 모든 알림을 띄운다`() {
        listOf("BODY_SHAPE_CHANGED", "CREW_CHAT", "OPPONENT_ARRIVED", null).forEach {
            assertTrue(isReleased(it, allOpen), "$it")
        }
    }

    @Test
    fun `종류 분류는 아바타 둘과 크루 열여섯이다`() {
        assertEquals(GatedFeature.AVATAR_CUSTOMIZE, GatedFeature.of("BACKGROUND_UNLOCKED"))
        assertEquals(GatedFeature.CREW, GatedFeature.of("REPORT_RESOLVED"))
        assertNull(GatedFeature.of("BATTLE_CORRECTED"))
    }
}
