package com.a207.runkk.app.ui.me

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.domain.account.MemberSettings
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** FR-015: 전송 범위를 먼저 알리고 선택 동의를 받는다. */
@RunWith(RobolectricTestRunner::class)
class LlmConsentScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `가입과 게스트 공용 화면은 집계값과 질문 원문 전송을 알리고 동의는 꺼져 있다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SignUpScreen(SignUpUiState(), isGuest = true, SignUpActions())
            }
        }
        compose.onNodeWithText("AI 리포트와 코치용 데이터 외부 전송", substring = true).performScrollTo().assertIsOff()
        compose.onNodeWithText(DISCLOSURE).performScrollTo().assertExists()
    }

    @Test
    fun `설정에서 LLM 동의는 안내를 확인하고 동의할 때만 저장한다`() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        showSettings(false) { item, agreed -> changes += item to agreed }
        compose.onNodeWithText("AI 리포트와 코치용 데이터 외부 전송 (선택)").performScrollTo().performClick()
        compose.onNodeWithText(DISCLOSURE).assertExists()
        assertEquals(emptyList(), changes)
        compose.onNodeWithText("동의하기").performClick()
        assertEquals(listOf(ConsentKeys.LLM_TRANSFER to true), changes)
    }

    @Test
    fun `LLM 안내를 취소하면 동의를 저장하지 않는다`() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        showSettings(false) { item, agreed -> changes += item to agreed }
        compose.onNodeWithText("AI 리포트와 코치용 데이터 외부 전송 (선택)").performScrollTo().performClick()
        compose.onNodeWithText("취소").performClick()
        assertEquals(emptyList(), changes)
    }

    @Test
    fun `이미 켜진 LLM 동의는 철회할 수 있다`() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        showSettings(true) { item, agreed -> changes += item to agreed }
        compose.onNodeWithText("AI 리포트와 코치용 데이터 외부 전송 (선택)").performScrollTo().performClick()
        assertEquals(listOf(ConsentKeys.LLM_TRANSFER to false), changes)
    }

    @Test
    fun `코치에서 들어오면 기존 동의를 철회하지 않고 최신 고지를 다시 확인한다`() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        showSettings(true, fromCoach = true) { item, agreed -> changes += item to agreed }
        compose.onNodeWithText("AI 코치 이용을 위한 동의 확인").assertExists()
        compose.onNodeWithText("AI 리포트와 코치용 데이터 외부 전송 (선택)").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithText("대화는 마지막 질문 뒤 90일 동안 보관해요.").assertExists()
        assertEquals(emptyList(), changes)
        compose.onNodeWithText("동의하기").performClick()
        assertEquals(listOf(ConsentKeys.LLM_TRANSFER to true), changes)
    }

    private fun showSettings(agreed: Boolean, fromCoach: Boolean = false, onConsent: (String, Boolean) -> Unit) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    SettingsPage.CONSENTS,
                    SettingsUiState(
                        isLoading = false,
                        settings = MemberSettings(emptyMap(), mapOf(ConsentKeys.LLM_TRANSFER to agreed))
                    ),
                    SettingsActions(setConsent = onConsent),
                    fromCoach = fromCoach
                )
            }
        }
    }

    private companion object {
        const val DISCLOSURE = "AI 리포트 코멘트와 코치 답변을 만들기 위해 러닝 집계값과 코치에 입력한 질문 원문을 외부 LLM 서비스로 보내요. " +
            "처리위탁과 국외 이전이 포함돼요. 동의하지 않아도 규칙 기반 리포트 코멘트는 볼 수 있지만 AI 코치는 이용할 수 없어요. " +
            "동의는 설정에서 언제든 철회할 수 있어요."
    }
}
