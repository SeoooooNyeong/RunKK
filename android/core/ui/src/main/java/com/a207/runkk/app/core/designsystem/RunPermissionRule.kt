package com.a207.runkk.app.core.designsystem

import android.Manifest

/** 신체 활동 권한(ACTIVITY_RECOGNITION)이 런타임 권한이 된 API 수준(Android 10). 그 전에는 걸음 센서를 권한 없이 쓴다. */
const val ACTIVITY_RECOGNITION_API = 29

/**
 * 측정 화면이 처음 열릴 때 한 번에 물을 권한(권한 창이 겹치지 않게 하나로 묻는다).
 * - 진행 알림(POST_NOTIFICATIONS, D-259): [shouldAskNotificationPermission] 규칙.
 * - 신체 활동(ACTIVITY_RECOGNITION, S15P31A207-242): 걸음 센서로 케이던스를 잰다. Android 10 이상이고 권한이 없을 때.
 * 이 화면에서 이미 물었으면([isAsked]) 빈 목록이다. 둘 다 거부해도 측정은 한다(케이던스 없이 저장하고 서버가 비운다).
 */
fun runPermissionsToAsk(
    sdkInt: Int,
    isNotificationGranted: Boolean,
    isActivityGranted: Boolean,
    isAsked: Boolean
): List<String> = buildList {
    if (isAsked) return@buildList
    if (shouldAskNotificationPermission(sdkInt, isNotificationGranted, isAsked = false)) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (sdkInt >= ACTIVITY_RECOGNITION_API && !isActivityGranted) add(Manifest.permission.ACTIVITY_RECOGNITION)
}
