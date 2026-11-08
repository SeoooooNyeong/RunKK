package com.a207.runkk.app.core.designsystem

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals

/** runPermissionsToAsk(D-259, S15P31A207-242): 측정 화면이 한 번에 묻는 권한. */
class RunPermissionRuleTest {
    @Test
    fun `Android 13 이상에서 둘 다 없으면 알림과 신체 활동을 함께 묻는다`() {
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACTIVITY_RECOGNITION),
            runPermissionsToAsk(sdkInt = 34, isNotificationGranted = false, isActivityGranted = false, isAsked = false)
        )
    }

    @Test
    fun `이미 있는 권한과 그 API 전의 권한은 묻지 않는다`() {
        assertEquals(
            listOf(Manifest.permission.ACTIVITY_RECOGNITION),
            runPermissionsToAsk(sdkInt = 34, isNotificationGranted = true, isActivityGranted = false, isAsked = false)
        )
        assertEquals(
            listOf(Manifest.permission.ACTIVITY_RECOGNITION),
            runPermissionsToAsk(sdkInt = 29, isNotificationGranted = false, isActivityGranted = false, isAsked = false)
        )
        assertEquals(
            emptyList(),
            runPermissionsToAsk(sdkInt = 28, isNotificationGranted = false, isActivityGranted = false, isAsked = false)
        )
    }

    @Test
    fun `이 화면에서 이미 물었으면 다시 묻지 않는다`() {
        assertEquals(
            emptyList(),
            runPermissionsToAsk(sdkInt = 34, isNotificationGranted = false, isActivityGranted = false, isAsked = true)
        )
    }
}
