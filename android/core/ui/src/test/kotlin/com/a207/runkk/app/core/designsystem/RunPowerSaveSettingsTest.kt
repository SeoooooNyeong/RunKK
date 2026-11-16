package com.a207.runkk.app.core.designsystem

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

/** 러닝 출발 전 절전 모드 판정과 설정 열기([임시:FR-성-380], [임시:FR-성-381], [임시:D-성-900]). */
@RunWith(RobolectricTestRunner::class)
class RunPowerSaveSettingsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val power = shadowOf(app.getSystemService(PowerManager::class.java))

    @Test
    fun `TC-성-11060 절전 모드가 켜져 있을 때만 안내 대상이다`() {
        power.setIsPowerSaveMode(true)
        assertTrue(shouldOfferRunPowerSaveSettings(app))
        power.setIsPowerSaveMode(false)
        assertFalse(shouldOfferRunPowerSaveSettings(app))
    }

    @Test
    fun `TC-성-11060 앱별 배터리 최적화 예외 여부는 판정에 쓰지 않는다`() {
        power.setIsPowerSaveMode(false)
        power.setIgnoringBatteryOptimizations(app.packageName, false)
        assertFalse(shouldOfferRunPowerSaveSettings(app), "예외가 아니어도 절전 모드가 꺼져 있으면 묻지 않는다")
        power.setIsPowerSaveMode(true)
        power.setIgnoringBatteryOptimizations(app.packageName, true)
        assertTrue(shouldOfferRunPowerSaveSettings(app), "예외여도 절전 모드가 켜져 있으면 묻는다")
    }

    @Test
    fun `TC-성-11061 절전 모드 설정을 먼저 열고 성공하면 다른 화면은 열지 않는다`() {
        val context = SettingsContext(app, setOf(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        assertTrue(openRunPowerSaveSettings(context))
        assertEquals(listOf(Settings.ACTION_BATTERY_SAVER_SETTINGS), context.attempts.map { it.action })
        assertTrue(context.attempts.all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })
    }

    @Test
    fun `TC-성-11061 절전 모드 설정이 없으면 배터리 사용량, 그것도 없으면 현재 앱 상세를 연다`() {
        val usage = SettingsContext(app, setOf(Intent.ACTION_POWER_USAGE_SUMMARY))
        assertTrue(openRunPowerSaveSettings(usage))
        assertEquals(
            listOf(Settings.ACTION_BATTERY_SAVER_SETTINGS, Intent.ACTION_POWER_USAGE_SUMMARY),
            usage.attempts.map { it.action }
        )

        val details = SettingsContext(app, setOf(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
        assertTrue(openRunPowerSaveSettings(details))
        assertEquals(
            listOf(
                Settings.ACTION_BATTERY_SAVER_SETTINGS,
                Intent.ACTION_POWER_USAGE_SUMMARY,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ),
            details.attempts.map { it.action }
        )
        assertEquals("package:${app.packageName}", details.attempts.last().dataString)
        assertTrue(details.attempts.all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })
    }

    @Test
    fun `TC-성-11061 접근이 거부된 설정은 건너뛰고 셋 다 열 수 없으면 false다`() {
        val denied =
            SettingsContext(
                app,
                setOf(Intent.ACTION_POWER_USAGE_SUMMARY),
                denied = Settings.ACTION_BATTERY_SAVER_SETTINGS
            )
        assertTrue(openRunPowerSaveSettings(denied))
        assertEquals(2, denied.attempts.size)

        val none = SettingsContext(app, emptySet())
        assertFalse(openRunPowerSaveSettings(none))
        assertEquals(3, none.attempts.size)
    }

    /** 지원하는 설정 화면만 열리는 Context. [denied] 화면은 접근 거부(SecurityException)를 던진다. */
    private class SettingsContext(
        context: Context,
        private val supported: Set<String>,
        private val denied: String? = null
    ) : ContextWrapper(context) {
        val attempts = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            attempts += intent
            if (intent.action == denied) throw SecurityException()
            if (intent.action !in supported) throw ActivityNotFoundException()
        }
    }
}
