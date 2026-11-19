package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
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
 * 시작 로딩 화면(S15P31A207-345): 로고 아래 상태 문구(정중한 알림 영역)·처음 설치 안내, 얇은 진행 막대와 퍼센트, 받을 것이 없으면
 * 막대 없음, 받지 못했으면 「준비를 마치지 못했어요」·「인터넷 연결을 확인해 주세요」와 「다시 시도」.
 * 기준 그림: 막대 중간(42%, 랭킹 문구), 시작 문구(막대 없음)·처음 설치 안내, 거의 끝, 재시도 중, 실패 화면의 다크·라이트.
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
        compose.onNodeWithText("준비를 마치지 못했어요").assertDoesNotExist()
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

        compose.onNodeWithText("준비를 마치지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("인터넷 연결을 확인해 주세요").assertIsDisplayed()
        compose.onNodeWithText("71%").assertDoesNotExist()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `상태 문구는 정중한 알림 영역으로 보이고 처음 설치 때만 안내를 덧붙인다`() {
        var isFirst by mutableStateOf(true)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkStartLoading(
                    percent = 10,
                    isFailed = false,
                    onRetry = {},
                    message = StartLoadingMessage.MY_AVATAR_FETCH,
                    isFirstInstall = isFirst
                )
            }
        }

        compose.onNodeWithText("내 아바타를 불러오고 있어요").assertIsDisplayed()
        compose.onNodeWithTag(START_LOADING_MESSAGE_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("처음 실행을 준비하고 있어요").assertIsDisplayed()

        isFirst = false
        compose.onNodeWithText("처음 실행을 준비하고 있어요").assertDoesNotExist()
    }

    @Test
    fun `목록을 만드는 동안은 막대 없이 시작 문구만 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkStartLoading(percent = null, isFailed = false, onRetry = {}, message = StartLoadingMessage.START)
            }
        }
        compose.onNodeWithText("시작할 준비를 하고 있어요").assertIsDisplayed()
        compose.onNodeWithTag(START_LOADING_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun `모든 문구는 Codex 최종안 그대로다`() {
        val texts = mutableMapOf<StartLoadingMessage, String>()
        compose.setContent {
            StartLoadingMessage.entries.forEach { texts[it] = androidx.compose.ui.res.stringResource(it.text) }
        }
        compose.waitForIdle()
        assertEquals(
            mapOf(
                StartLoadingMessage.START to "시작할 준비를 하고 있어요",
                StartLoadingMessage.MY_AVATAR_FETCH to "내 아바타를 불러오고 있어요",
                StartLoadingMessage.MY_AVATAR_PREPARE to "내 아바타를 준비하고 있어요",
                StartLoadingMessage.RANKING to "랭킹을 불러오고 있어요",
                StartLoadingMessage.CELEB to "셀럽런을 불러오고 있어요",
                StartLoadingMessage.RECORDS_CREW_FRIENDS to "기록, 크루, 친구를 불러오고 있어요",
                StartLoadingMessage.ALMOST_DONE to "준비가 거의 끝났어요",
                StartLoadingMessage.RETRYING to "연결이 느려 다시 시도하고 있어요"
            ),
            texts.toMap()
        )
    }

    @Test
    fun `기준 그림 시작 문구와 처음 설치 안내 다크`() = compose.captureRunkkScreen("start_loading_start_first") {
        RunkkStartLoading(
            percent = null,
            isFailed = false,
            onRetry = {},
            message = StartLoadingMessage.START,
            isFirstInstall = true
        )
    }

    @Test
    fun `기준 그림 시작 문구와 처음 설치 안내 라이트`() =
        compose.captureRunkkScreen("start_loading_start_first_light", isDarkTheme = false) {
            RunkkStartLoading(
                percent = null,
                isFailed = false,
                onRetry = {},
                message = StartLoadingMessage.START,
                isFirstInstall = true
            )
        }

    @Test
    fun `기준 그림 거의 끝 다크`() = compose.captureRunkkScreen("start_loading_almost_done") {
        RunkkStartLoading(percent = 93, isFailed = false, onRetry = {}, message = StartLoadingMessage.ALMOST_DONE)
    }

    @Test
    fun `기준 그림 재시도 중 라이트`() = compose.captureRunkkScreen("start_loading_retrying_light", isDarkTheme = false) {
        RunkkStartLoading(percent = 35, isFailed = false, onRetry = {}, message = StartLoadingMessage.RETRYING)
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
    private fun Progress() =
        RunkkStartLoading(percent = 42, isFailed = false, onRetry = {}, message = StartLoadingMessage.RANKING)

    @androidx.compose.runtime.Composable
    private fun Failed() = RunkkStartLoading(percent = 71, isFailed = true, onRetry = {})
}
