package com.a207.runkk.app

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 화면 방향: 폰과 폴드 바깥 화면은 세로로 고정한다. 짧은 변 600dp 이상(폴드 펼침·태블릿)은 Android 16(targetSdk 36)이
 * 고정을 무시하므로 화면이 가로에서도 깨지지 않게 그린다(홈 배경 바닥 기준 자르기 등).
 */
@RunWith(RobolectricTestRunner::class)
class ScreenOrientationTest {
    @Test
    fun `메인 화면은 세로로 고정한다`() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
    }
}
