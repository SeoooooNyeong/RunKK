package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 시작 로딩 화면(S15P31A207-345): 로고 아래 얇은 진행 막대와 퍼센트, 받을 것이 없으면 막대 없음, 받지 못했으면 안내와 「다시 시도」.
 * 기준 그림: 막대 중간(42%)과 실패 화면의 다크·라이트.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunkkStartLoadingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `받는 중이면 로고 아래 막대와 퍼센트를 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunkkStartLoading(percent = 42, isFailed = false, onRetry = {}) }
        }

        compose.onNodeWithText("42%").assertIsDisplayed()
        compose.onNodeWithTag(START_LOADING_BAR_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(
                        0.42f,
                        0f..1f
                    )
                )
            )
        compose.onNodeWithText("에셋을 받지 못했어요").assertDoesNotExist()
    }

    @Test
    fun `받을 것을 아직 모르거나 없으면 막대를 그리지 않는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunkkStartLoading(percent = null, isFailed = false, onRetry = {}) }
        }

        compose.onNodeWithTag(START_LOADING_BAR_TAG).assertDoesNotExist()
        compose.onNodeWithText("0%").assertDoesNotExist()
    }

    @Test
    fun `받지 못했으면 안내와 다시 시도 단추를 보이고 누르면 알린다`() {
        var retries = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkStartLoading(percent = 71, isFailed = true, onRetry = { retries++ })
            }
        }

        compose.onNodeWithText("에셋을 받지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("71%").assertDoesNotExist()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `기준 그림 막대 중간 다크`() = compose.captureRunkkScreen("start_loading_progress") { Progress() }

    @Test
    fun `기준 그림 막대 중간 라이트`() =
        compose.captureRunkkScreen("start_loading_progress_light", isDarkTheme = false) { Progress() }

    @Test
    fun `기준 그림 실패 다크`() = compose.captureRunkkScreen("start_loading_failed") { Failed() }

    @Test
    fun `기준 그림 실패 라이트`() = compose.captureRunkkScreen("start_loading_failed_light", isDarkTheme = false) { Failed() }

    @androidx.compose.runtime.Composable
    private fun Progress() = RunkkStartLoading(percent = 42, isFailed = false, onRetry = {})

    @androidx.compose.runtime.Composable
    private fun Failed() = RunkkStartLoading(percent = 71, isFailed = true, onRetry = {})
}
