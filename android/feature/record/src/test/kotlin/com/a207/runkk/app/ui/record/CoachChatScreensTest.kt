package com.a207.runkk.app.ui.record

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.domain.coach.CoachConversation
import com.a207.runkk.app.domain.coach.CoachFailure
import com.a207.runkk.app.ui.record.coach.CoachChatActions
import com.a207.runkk.app.ui.record.coach.CoachChatScreen
import com.a207.runkk.app.ui.record.coach.CoachChatTurn
import com.a207.runkk.app.ui.record.coach.CoachChatUiState
import java.time.LocalDateTime
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
        compose.onNodeWithContentDescription("질문 보내기").assertIsNotEnabled()
        compose.onNodeWithText("질문").performTextInput("오늘은 어떻게 달릴까요?")
        compose.onNodeWithContentDescription("질문 보내기").assertIsEnabled().performClick()
        assertEquals(listOf("오늘은 어떻게 달릴까요?"), questions)
        compose.onNodeWithContentDescription("질문 보내기").assertIsNotEnabled()
    }

    @Test
    fun `501자 질문은 보낼 수 없다`() {
        compose.setContent { RunkkTheme { CoachChatScreen(CoachChatUiState(), CoachChatActions()) } }
        compose.onNodeWithText("질문").performTextInput("가".repeat(501))
        compose.onNodeWithContentDescription("질문 보내기").assertIsNotEnabled()
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
        compose.onNodeWithText("동의 설정으로 이동").performClick()
        assertEquals(1, opened)
        compose.onNodeWithContentDescription("질문 보내기").assertDoesNotExist()
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
    fun `오류 여섯 종류마다 안내 문구와 복구 버튼을 보인다`() {
        val failure = mutableStateOf(CoachFailure.RETRY)
        compose.setContent {
            RunkkTheme {
                CoachChatScreen(CoachChatUiState(failure = failure.value), CoachChatActions())
            }
        }
        val messages = mapOf(
            CoachFailure.RETRY to "답을 만들지 못했어요. 다시 물어봐 주세요",
            CoachFailure.QUOTA to "오늘 질문을 모두 썼어요. 내일 다시 물어봐 주세요",
            CoachFailure.EXPIRED to "보관 기간이 지난 대화예요. 새 대화를 열어 주세요",
            CoachFailure.TOO_LONG to "질문은 1자 이상 500자 이하로 입력해 주세요",
            CoachFailure.CONSENT_REQUIRED to "AI 코치를 이용하려면 동의가 필요해요",
            CoachFailure.FEATURE_CLOSED to "AI 코치는 아직 준비 중이에요"
        )
        CoachFailure.entries.forEach { reason ->
            compose.runOnIdle { failure.value = reason }
            compose.onNodeWithText(messages.getValue(reason)).performScrollTo().assertExists()
            if (reason == CoachFailure.RETRY) {
                compose.onNodeWithText("다시 묻기").assertExists()
            } else {
                compose.onNodeWithText("다시 묻기").assertDoesNotExist()
            }
            if (reason == CoachFailure.CONSENT_REQUIRED) {
                compose.onNodeWithText("동의 설정으로 이동").assertExists()
            } else {
                compose.onNodeWithText("동의 설정으로 이동").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `이전 대화를 누르면 목록이 보이고 고른 대화를 연 뒤 코치 화면으로 돌아온다`() {
        val opened = mutableListOf<String>()
        val conversation = CoachConversation("c1", "어제 러닝 어땠어?", LocalDateTime.of(2026, 11, 2, 9, 0))
        compose.setContent {
            RunkkTheme {
                CoachChatScreen(
                    CoachChatUiState(conversations = listOf(conversation)),
                    CoachChatActions(onOpenConversation = { opened += it })
                )
            }
        }
        compose.onNodeWithText("이전 대화").performClick()
        compose.onNodeWithText("11월 2일").assertExists()
        compose.onNodeWithText("어제 러닝 어땠어?").performClick()
        assertEquals(listOf("c1"), opened)
        compose.onNodeWithText("AI 코치").assertExists()
    }

    @Test
    fun `이전 대화가 없으면 새 대화를 열 수 있다`() {
        var opened = 0
        compose.setContent {
            RunkkTheme {
                CoachChatScreen(CoachChatUiState(), CoachChatActions(onNewConversation = { opened++ }))
            }
        }
        compose.onNodeWithText("이전 대화").performClick()
        compose.onNodeWithText("보관 중인 이전 대화가 없어요").assertExists()
        compose.onNodeWithText("새 대화").performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("새 대화를 시작해요").assertExists()
    }

    @Test
    fun chatScreenshot() = compose.captureRunkkScreen("record_ai_coach") {
        CoachChatScreen(
            CoachChatUiState(
                conversationId = "c1",
                turns = listOf(
                    CoachChatTurn("어제 러닝 어땠어?", "후반 3km에서 페이스를 잘 지켰어요. 같은 심박에서 지난주보다 12초 빨라졌어요.", true),
                    CoachChatTurn("이번 주에 뭘 하면 좋을까?", "회복 러닝을 한 번 넣고")
                ),
                isSending = true
            ),
            CoachChatActions()
        )
    }

    @Test
    fun newChatScreenshot() = compose.captureRunkkScreen("record_ai_coach_new") {
        CoachChatScreen(CoachChatUiState(), CoachChatActions())
    }

    @Test
    fun `이전 대화 목록은 지난 문답을 볼 수 있다고 안내한다`() {
        val conversation = CoachConversation("c1", "어제 러닝 어땠어?", LocalDateTime.of(2026, 11, 2, 9, 0))
        compose.setContent {
            RunkkTheme { CoachChatScreen(CoachChatUiState(conversations = listOf(conversation)), CoachChatActions()) }
        }
        compose.onNodeWithText("이전 대화").performClick()
        compose.onNodeWithText("대화를 선택하면 지난 문답을 보고 이어서 질문할 수 있어요.", substring = true).assertExists()
        compose.onNodeWithText("표시되지 않아요", substring = true).assertDoesNotExist()
    }

    @Test
    fun `이어가는 대화는 지난 문답 말풍선을 보이고 그 아래로 질문할 수 있다`() {
        compose.setContent { RunkkTheme { CoachChatScreen(continuing(), CoachChatActions()) } }
        compose.onNodeWithText("선택한 대화로 이어가요").assertExists()
        compose.onNodeWithText("아래 지난 문답에 이어서 질문하면 그 대화의 맥락을 참고해 답해요.").assertExists()
        compose.onNodeWithText("어제 러닝 어땠어?").performScrollTo().assertExists()
        compose.onNodeWithText("후반 3km에서 페이스를 잘 지켰어요.").performScrollTo().assertExists()
        compose.onNodeWithText("이번 주에 뭘 하면 좋을까?").performScrollTo().assertExists()
        compose.onNodeWithText("질문").performTextInput("이어서 물어요")
        compose.onNodeWithContentDescription("질문 보내기").assertIsEnabled()
    }

    @Test
    fun `지난 문답을 읽는 중에는 안내를 보이고 실패하면 다시 불러온다`() {
        var reloaded = 0
        val state = mutableStateOf(continuing().copy(history = emptyList(), isLoadingHistory = true))
        compose.setContent {
            RunkkTheme { CoachChatScreen(state.value, CoachChatActions(onReloadHistory = { reloaded++ })) }
        }
        compose.onNodeWithText("지난 문답을 불러오고 있어요").assertExists()
        compose.onNodeWithText("질문").performTextInput("너무 빠른 질문")
        compose.onNodeWithContentDescription("질문 보내기").assertIsNotEnabled()

        compose.runOnIdle {
            state.value = state.value.copy(isLoadingHistory = false, historyError = UiText.Plain("네트워크 연결을 확인해 주세요"))
        }
        compose.onNodeWithText("지난 문답을 불러오지 못했어요").assertExists()
        compose.onNodeWithText("다시 불러오기").performScrollTo().performClick()
        assertEquals(1, reloaded)

        compose.runOnIdle { state.value = state.value.copy(historyError = null) }
        compose.onNodeWithText("표시할 지난 문답이 없어요. 이어서 질문할 수 있어요.").assertExists()
    }

    @Test
    fun continueChatScreenshot() = compose.captureRunkkScreen("record_ai_coach_continue") {
        CoachChatScreen(continuing(), CoachChatActions())
    }

    private fun continuing() = CoachChatUiState(
        conversationId = "c1",
        conversations = listOf(CoachConversation("c1", "어제 러닝 어땠어?", LocalDateTime.of(2026, 11, 2, 9, 0))),
        history = listOf(
            CoachChatTurn("어제 러닝 어땠어?", "후반 3km에서 페이스를 잘 지켰어요.", true),
            CoachChatTurn("이번 주에 뭘 하면 좋을까?", "회복 러닝을 한 번 넣고 주말에 길게 달려 보세요.", true)
        )
    )
}
