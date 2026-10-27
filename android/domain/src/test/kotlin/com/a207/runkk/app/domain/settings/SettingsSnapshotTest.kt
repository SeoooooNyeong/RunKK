package com.a207.runkk.app.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 설정 사본이 없을 때의 값(D-162, TC-6526 도메인 쪽). */
class SettingsSnapshotTest {
    @Test
    fun `사본이 없으면 개인정보 항목은 거짓, 테마는 SYSTEM, 근접 알림은 참이다`() {
        val safe = SettingsSnapshot.SAFE
        assertFalse(safe.isLocationConsented)
        assertFalse(safe.isHeartRateConsented)
        assertFalse(safe.isCourseMapShared)
        assertEquals(ScreenTheme.SYSTEM, safe.theme)
        assertTrue(safe.guidanceSettings.isProximityAlertOn)
        assertFalse(safe.guidanceSettings.isFollowingCoachPlan)
    }
}
