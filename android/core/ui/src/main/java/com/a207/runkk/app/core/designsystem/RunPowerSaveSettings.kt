package com.a207.runkk.app.core.designsystem

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * 러닝 출발 전에 절전 모드 안내가 필요한가([임시:FR-성-380], [임시:D-성-900]). 기기 절전 모드가 켜져 있을 때만 true다.
 * 앱별 배터리 최적화 예외는 읽지 않는다(U-40).
 */
fun shouldOfferRunPowerSaveSettings(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

/**
 * 절전 모드를 끌 수 있는 설정 화면을 연다([임시:FR-성-381], [임시:D-성-900]). 절전 모드 설정 → 배터리 사용량 → 현재 앱 상세 순으로
 * 시도해 처음 열린 화면에서 멈춘다. 화면이 없거나 접근이 거부되면 다음 후보로 넘어가고, 셋 다 열지 못하면 false다(호출자가 출발을 잇는다).
 */
fun openRunPowerSaveSettings(context: Context): Boolean = runPowerSaveSettingsIntents(context).any { intent ->
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun runPowerSaveSettingsIntents(context: Context): List<Intent> = listOf(
    Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
    Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
)
