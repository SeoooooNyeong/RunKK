package com.a207.runkk.app.ui.record

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.domain.coach.CoachFailure
import com.a207.runkk.app.ui.record.coach.CoachChatActions
import com.a207.runkk.app.ui.record.coach.CoachChatScreen
import com.a207.runkk.app.ui.record.coach.CoachChatTurn
import com.a207.runkk.app.ui.record.coach.CoachChatUiState
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class CoachChatScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `빈 질문은 보내지 못하고 입력한 질문을 보낸다`() {
        val questions = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CoachChatScreen(CoachChatUiState(), CoachChatActions(onSend = { questions += it }))
            }
        }
        compose.onNodeWithText("질문 보내기").assertIsNotEnabled()
        compose.onNodeWithText("질문").performTextInput("오늘은 어떻게 달릴까요?")
        compose.onNodeWithText("질문 보내기").assertIsEnabled().performClick()
        assertEquals(listOf("오늘은 어떻게 달릴까요?"), questions)
        compose.onNodeWithText("질문 보내기").assertIsNotEnabled()
    }

    @Test
    fun `501자 질문은 보낼 수 없다`() {
        compose.setContent { RunkkTheme { CoachChatScreen(CoachChatUiState(), CoachChatActions()) } }
        compose.onNodeWithText("질문").performTextInput("가".repeat(501))
        compose.onNodeWithText("질문 보내기").assertIsNotEnabled()
        compose.onNodeWithText("질문은 1자 이상 500자 이하로 입력해 주세요").assertExists()
    }

    @Test
    fun `실패한 일부 답을 남기고 다시 묻기를 제공한다`() {
        var retried = 0
        compose.setContent {
            RunkkTheme {
                CoachChatScreen(
                    CoachChatUiState(turns = listOf(CoachChatTurn("회복 방법", "오늘은 쉬면서")), failure = CoachFailure.RETRY),
                    CoachChatActions(onRetry = { retried++ })
                )
            }
        }
        compose.onNodeWithText("오늘은 쉬면서").assertExists()
        compose.onNodeWithText("답을 만들지 못했어요. 다시 물어봐 주세요").assertExists()
        compose.onNodeWithText("다시 묻기").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `미동의는 설정 이동을 안내하고 질문 보내기는 막는다`() {
        var opened = 0
        compose.setContent {
            RunkkTheme {
                CoachChatScreen(
                    CoachChatUiState(failure = CoachFailure.CONSENT_REQUIRED),
                    CoachChatActions(onConsents = { opened++ })
                )
            }
        }
        compose.onNodeWithText("동의 설정").performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("질문").performTextInput("질문")
        compose.onNodeWithText("질문 보내기").assertIsNotEnabled()
    }

    @Test
    fun `한도 초과는 다시 묻기 없이 내일 안내를 보인다`() {
        compose.setContent {
            RunkkTheme {
                CoachChatScreen(CoachChatUiState(failure = CoachFailure.QUOTA), CoachChatActions())
            }
        }
        compose.onNodeWithText("오늘 질문을 모두 썼어요. 내일 다시 물어봐 주세요").assertExists()
        compose.onNodeWithText("다시 묻기").assertDoesNotExist()
    }

    @Test
    fun `만료된 대화에서는 새 대화를 열 수 있다`() {
        var opened = 0
        compose.setContent {
            RunkkTheme {
                CoachChatScreen(
                    CoachChatUiState(failure = CoachFailure.EXPIRED),
                    CoachChatActions(onNewConversation = { opened++ })
                )
            }
        }
        compose.onNodeWithText("새 대화").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun chatScreenshot() = compose.captureRunkkScreen("record_ai_coach") {
        CoachChatScreen(
            CoachChatUiState(turns = listOf(CoachChatTurn("오늘은 어떻게 달릴까요?", "천천히 달리며 회복해 보세요.", true))),
            CoachChatActions()
        )
    }
}
