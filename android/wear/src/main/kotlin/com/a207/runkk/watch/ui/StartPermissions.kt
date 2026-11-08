package com.a207.runkk.watch.ui

import android.Manifest
import android.os.Build

/**
 * 워치 러닝 시작 조작 때 물을 권한(WatchActivity, FR-245, S15P31A207-242). 위치가 없으면 위치·심박·알림(API 33+)·신체 활동을 함께,
 * 위치가 있으면 신체 활동 권한이 없고 이 화면에서 아직 묻지 않았을 때만 신체 활동 하나를 묻는다. 빈 목록이면 바로 시작한다.
 * 신체 활동 권한은 Health Services 속도·케이던스에 쓰고, 거절해도 시작한다.
 */
internal fun startPermissionsToAsk(
    hasLocation: Boolean,
    hasActivity: Boolean,
    isActivityAsked: Boolean,
    heartRatePermission: String,
    sdkInt: Int
): List<String> = buildList {
    if (!hasLocation) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(heartRatePermission)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        if (!hasActivity) add(Manifest.permission.ACTIVITY_RECOGNITION)
    } else if (!hasActivity && !isActivityAsked) {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
    }
}
