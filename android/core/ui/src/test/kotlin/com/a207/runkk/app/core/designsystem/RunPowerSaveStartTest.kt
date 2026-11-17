package com.a207.runkk.app.core.designsystem

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** 모든 폰 러닝 출발 전 절전 모드 안내([임시:FR-성-380]~[임시:FR-성-382], [임시:NFR-성-20], [임시:D-성-901]). */
@RunWith(RobolectricTestRunner::class)
class RunPowerSaveStartTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = RuntimeEnvironment.getApplication()
    private val power = shadowOf(app.getSystemService(PowerManager::class.java))
    private val calls = mutableListOf<String>()
    private lateinit var request: () -> Unit

    @Composable
    private fun Guard() {
        request = rememberRunPowerSaveStart(onCancel = { calls += "cancel" }, onStart = { calls += "start" })
    }

    private fun show(context: Context? = null) = compose.setContent {
        RunkkTheme(reduceMotion = true) {
            if (context == null) Guard() else CompositionLocalProvider(LocalContext provides context) { Guard() }
        }
    }

    private fun requestStart() {
        compose.runOnIdle { request() }
        compose.waitForIdle()
    }

    private fun assertPromptVisible(isVisible: Boolean) {
        if (isVisible) compose.onNode(isDialog()).assertExists() else compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `TC-성-11062 절전 모드가 꺼져 있으면 묻지 않고 바로 출발한다`() {
        power.setIsPowerSaveMode(false)
        show()
        requestStart()
        assertEquals(listOf("start"), calls)
        assertPromptVisible(false)
    }

    @Test
    fun `TC-성-11062 절전 모드가 켜져 있으면 출발을 보류하고 짧은 안내와 두 단추를 보인다`() {
        power.setIsPowerSaveMode(true)
        show()
        requestStart()
        requestStart()
        assertEquals(emptyList(), calls, "안내 중 다시 누른 출발은 무시한다")
        assertPromptVisible(true)
        compose.onNodeWithText("절전 모드를 꺼 주세요").assertExists()
        compose.onNodeWithText("화면을 끄면 기록이 끊길 수 있어요.").assertExists()
        compose.onNodeWithText("설정 열기").assertExists()
        compose.onNodeWithText("그냥 시작").assertExists()
    }

    @Test
    fun `TC-성-11063 그냥 시작은 설정 없이 한 번만 출발하고 같은 화면에서는 다시 묻지 않는다`() {
        power.setIsPowerSaveMode(true)
        show()
        requestStart()
        compose.onNodeWithText("그냥 시작").performClick()
        assertEquals(listOf("start"), calls)
        assertPromptVisible(false)

        requestStart()
        assertEquals(listOf("start", "start"), calls)
        assertPromptVisible(false)
    }

    @Test
    fun `TC-성-11064 뒤로 가기로 닫으면 출발하지 않고 취소를 알리며 같은 화면에서는 다시 묻지 않는다`() {
        power.setIsPowerSaveMode(true)
        show()
        requestStart()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf("cancel"), calls)
        assertPromptVisible(false)

        requestStart()
        assertEquals(listOf("cancel", "start"), calls)
    }

    @Test
    fun `TC-성-11065 설정을 여는 동안 안내를 유지하고 절전 모드가 켜진 채 돌아오면 다시 고르게 한다`() {
        power.setIsPowerSaveMode(true)
        val context = SettingsContext(compose.activity, setOf(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        show(context)
        requestStart()
        compose.onNodeWithText("설정 열기").performClick()
        assertEquals(listOf(Settings.ACTION_BATTERY_SAVER_SETTINGS), context.attempts.map { it.action })
        assertEquals(emptyList(), calls)
        assertPromptVisible(true)

        returnFromSettings()
        assertEquals(emptyList(), calls, "절전 모드가 여전히 켜져 있으면 출발하지 않는다")
        assertPromptVisible(true)
    }

    @Test
    fun `TC-성-11065 설정에서 절전 모드를 끄고 돌아오면 보류한 출발을 한 번 잇는다`() {
        power.setIsPowerSaveMode(true)
        val context = SettingsContext(compose.activity, setOf(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        show(context)
        requestStart()
        compose.onNodeWithText("설정 열기").performClick()

        power.setIsPowerSaveMode(false)
        returnFromSettings()
        assertEquals(listOf("start"), calls)
        assertPromptVisible(false)

        returnFromSettings()
        assertEquals(listOf("start"), calls, "다시 돌아와도 두 번 출발하지 않는다")
    }

    @Test
    fun `TC-성-11065 열 수 있는 설정 화면이 없으면 출발을 잇는다`() {
        power.setIsPowerSaveMode(true)
        val context = SettingsContext(compose.activity, emptySet())
        show(context)
        requestStart()
        compose.onNodeWithText("설정 열기").performClick()
        assertEquals(3, context.attempts.size)
        assertEquals(listOf("start"), calls)
        assertPromptVisible(false)
    }

    @Test
    fun `TC-성-11066 화면 회전 뒤에도 안내 상태를 지키고 본 뒤에는 다시 묻지 않는다`() {
        power.setIsPowerSaveMode(true)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RunkkTheme(reduceMotion = true) { Guard() } }
        requestStart()
        restoration.emulateSavedInstanceStateRestore()
        assertPromptVisible(true)
        assertEquals(emptyList(), calls)

        compose.onNodeWithText("그냥 시작").performClick()
        restoration.emulateSavedInstanceStateRestore()
        requestStart()
        assertEquals(listOf("start", "start"), calls)
        assertPromptVisible(false)
    }

    @Test
    fun `TC-성-11067 안내 문구는 한 문장이고 배터리 최적화 권장 표현이 없다`() {
        val title = app.getString(R.string.core_run_power_save_title)
        val message = app.getString(R.string.core_run_power_save_message)
        assertEquals("절전 모드를 꺼 주세요", title)
        assertEquals("화면을 끄면 기록이 끊길 수 있어요.", message)
        assertEquals("설정 열기", app.getString(R.string.core_run_power_save_settings))
        assertEquals("그냥 시작", app.getString(R.string.core_run_power_save_start))
        assertFalse('\n' in message)
        assertFalse('·' in title + message)
        assertEquals(1, message.count { it == '.' })

        val strings = R.string::class.java.fields.map { app.getString(it.getInt(null)) }
        assertTrue(strings.none { "제한 없음" in it || "배터리 최적화" in it })
    }

    private fun returnFromSettings() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    /** 지원하는 설정 화면만 열리는 Context. 실제 Activity로 옮기지 않고 시도만 적는다. */
    private class SettingsContext(context: Context, private val supported: Set<String>) : ContextWrapper(context) {
        val attempts = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            attempts += intent
            if (intent.action !in supported) throw ActivityNotFoundException()
        }
    }
}
