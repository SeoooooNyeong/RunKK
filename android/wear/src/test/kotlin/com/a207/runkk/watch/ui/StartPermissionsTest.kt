package com.a207.runkk.watch.ui

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals

/** startPermissionsToAsk(FR-245, S15P31A207-242): 워치 시작 조작 때 묻는 권한. */
class StartPermissionsTest {
    private val heart = "android.permission.health.READ_HEART_RATE"

    @Test
    fun `위치가 없으면 위치·심박·알림·신체 활동을 함께 묻는다`() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                heart,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACTIVITY_RECOGNITION
            ),
            startPermissionsToAsk(
                hasLocation = false,
                hasActivity = false,
                isActivityAsked = false,
                heartRatePermission = heart,
                sdkInt = 34
            )
        )
    }

    @Test
    fun `위치가 있으면 신체 활동만 이 화면에서 한 번 묻고 다 있으면 묻지 않는다`() {
        assertEquals(
            listOf(Manifest.permission.ACTIVITY_RECOGNITION),
            startPermissionsToAsk(
                true,
                hasActivity = false,
                isActivityAsked = false,
                heartRatePermission = heart,
                sdkInt = 34
            )
        )
        assertEquals(
            emptyList(),
            startPermissionsToAsk(
                true,
                hasActivity = false,
                isActivityAsked = true,
                heartRatePermission = heart,
                sdkInt = 34
            )
        )
        assertEquals(
            emptyList(),
            startPermissionsToAsk(
                true,
                hasActivity = true,
                isActivityAsked = false,
                heartRatePermission = heart,
                sdkInt = 34
            )
        )
    }
}
