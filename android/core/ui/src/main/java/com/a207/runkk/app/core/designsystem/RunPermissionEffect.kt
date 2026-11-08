package com.a207.runkk.app.core.designsystem

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 측정 화면이 진행 알림 권한과 신체 활동 권한(걸음 센서 케이던스, S15P31A207-242)을 한 번에 묻는다([runPermissionsToAsk]).
 * [shouldAsk]가 참이 되면 이 화면에서 한 번만 묻고, 결과와 관계없이 측정은 이어 간다. 자유런 카운트다운, 대결 진행(배틀런·친구 대결·
 * 배치고사·셀프런·셀럽런), 크루런 화면이 같은 규칙으로 부른다.
 */
@Composable
fun RunPermissionEffect(shouldAsk: Boolean = true) {
    val context = LocalContext.current
    var isAsked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(shouldAsk) {
        if (!shouldAsk) return@LaunchedEffect
        val asks = runPermissionsToAsk(
            sdkInt = Build.VERSION.SDK_INT,
            isNotificationGranted = context.isGranted(Manifest.permission.POST_NOTIFICATIONS),
            isActivityGranted = context.isGranted(Manifest.permission.ACTIVITY_RECOGNITION),
            isAsked = isAsked
        )
        if (asks.isNotEmpty()) {
            isAsked = true
            launcher.launch(asks.toTypedArray())
        }
    }
}

private fun Context.isGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
