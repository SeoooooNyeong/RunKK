package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TC-성-11068 러닝 출발 전 절전 모드 안내의 다크·라이트 기준 그림([임시:NFR-성-20]). 대화상자는 따로 뜬 창이라 화면째 찍는다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunPowerSaveDialogScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dark() = capture("run_power_save_dialog", isDarkTheme = true)

    @Test
    fun light() = capture("run_power_save_dialog_light", isDarkTheme = false)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, isDarkTheme: Boolean) {
        compose.setContent {
            RunkkTheme(darkTheme = isDarkTheme, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(RunkkColors.Background))
                RunPowerSaveDialog(onSettings = {}, onStart = {}, onDismissRequest = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}
