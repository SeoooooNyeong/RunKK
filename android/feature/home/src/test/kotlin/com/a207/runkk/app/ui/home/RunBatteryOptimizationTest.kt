package com.a207.runkk.app.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RunBatteryOptimizationTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun `사용자가 앱을 예외로 지정하면 시작 안내 대상에서 제외한다`() {
        val power = app.getSystemService(PowerManager::class.java)
        val shadow = shadowOf(power)
        shadow.setIgnoringBatteryOptimizations(app.packageName, false)
        assertTrue(shouldOfferRunBatterySettings(app))
        shadow.setIgnoringBatteryOptimizations(app.packageName, true)
        assertFalse(shouldOfferRunBatterySettings(app))
    }

    @Test
    fun `배터리 최적화 설정을 우선 열고 앱 설정은 열지 않는다`() {
        val context = SettingsContext(app, setOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        assertTrue(openRunBatterySettings(context))
        assertEquals(listOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), context.attempts.map { it.action })
    }

    @Test
    fun `최적화 화면이 없는 기기에서는 현재 앱의 설정을 연다`() {
        val context = SettingsContext(app, setOf(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
        assertTrue(openRunBatterySettings(context))
        assertEquals("package:${app.packageName}", context.attempts.last().dataString)
    }

    @Test
    fun `설정 화면이 없으면 호출자가 자유런 시작을 이어갈 수 있다`() {
        val context = SettingsContext(app, emptySet())
        assertFalse(openRunBatterySettings(context))
        assertEquals(2, context.attempts.size)
    }

    private class SettingsContext(context: Context, private val supported: Set<String>) : ContextWrapper(context) {
        val attempts = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            attempts += intent
            if (intent.action !in supported) throw ActivityNotFoundException()
        }
    }
}
