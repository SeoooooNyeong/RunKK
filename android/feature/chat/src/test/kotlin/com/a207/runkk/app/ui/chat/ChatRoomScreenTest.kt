package com.a207.runkk.app.ui.chat

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ReactionKind
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 채팅방 화면(ui.chat): 자리표시·다시 보내기·리액션·보내기·신고 진입과 라이트·다크 스크린샷(NFR-041). 신고 시트는 Robolectric에서 입력칸이 든 창이 유휴가 되지 않아 ViewModel 시험으로 본다. */
@RunWith(RobolectricTestRunner::class)
class ChatRoomScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private val actions =
        ChatActions(
            onSend = { calls += "send" },
            onRetry = { calls += "retry:${it.clientMessageId}" },
            onReaction = { m, kind -> calls += "reaction:${m.messageId}:$kind" },
            onReportAsk = { calls += "report:${it.messageId}" },
            onDeleteAsk = { calls += "delete:${it.messageId}" },
            onLoadOlder = { calls += "older" }
        )

    private fun show(state: ChatUiState) {
        compose.setContent { RunkkTheme(reduceMotion = true) { ChatRoomScreen(state, actions) } }
    }

    @Test
    fun `내 메시지 목록 키는 서버가 확정해 messageId가 붙어도 바뀌지 않는다(AN5-D-16)`() {
        val sending =
            chatMessage(null, isMine = true, clientMessageId = "c-1", sendState = ChatMessage.SendState.SENDING)
        val confirmed = chatMessage(102, isMine = true, clientMessageId = "c-1")

        assertEquals(sending.listKey(), confirmed.listKey())
        assertEquals("m:7", chatMessage(7).listKey(), "남의 메시지는 messageId")
    }

    @Test
    fun `삭제·숨김 메시지는 자리표시로 남고 리액션·신고가 없다(D-160)`() {
        show(ChatUiState(messages = listOf(chatMessage(101).removed())))
        compose.onNode(hasContentDescription("바람처럼, 오전 7:00, 삭제된 메시지")).assertExists()
        compose.onNode(
            hasContentDescription("삭제된 메시지", substring = true) and
                SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick)
        ).assertExists()
        compose.onNodeWithContentDescription("좋아요 0개").assertDoesNotExist()
    }

    @Test
    fun `보내지 못한 메시지는 실패를 보이고 다시 보내기를 누를 수 있다(D-160 (3))`() {
        show(
            ChatUiState(
                messages = listOf(
                    chatMessage(
                        null,
                        "안녕",
                        isMine = true,
                        sendState = ChatMessage.SendState.FAILED,
                        clientMessageId = "c1"
                    )
                )
            )
        )
        compose.onNodeWithText("보내지 못했어요").assertExists()
        compose.onNodeWithText("다시 보내기").performClick()
        assertEquals(listOf("retry:c1"), calls)
    }

    @Test
    fun `리액션은 수가 있는 것만 칩으로 보이고 나머지는 길게 눌러 단다, 남의 메시지는 신고(FR-210)`() {
        show(ChatUiState(messages = listOf(chatMessage(101, reactions = mapOf(ReactionKind.LIKE to 3)))))
        compose.onNodeWithContentDescription("하트 0개").assertDoesNotExist()
        compose.onNodeWithContentDescription("좋아요 3개").performClick()
        openMenu("m101")
        compose.onNodeWithContentDescription("하트 달기").performClick()
        openMenu("m101")
        compose.onNodeWithContentDescription("바람처럼 메시지 신고").performClick()
        assertEquals(listOf("reaction:101:LIKE", "reaction:101:HEART", "report:101"), calls)
    }

    @Test
    fun `내 메시지는 삭제만 있고 신고가 없다`() {
        show(ChatUiState(messages = listOf(chatMessage(101, isMine = true))))
        openMenu("m101")
        compose.onNodeWithText("신고").assertDoesNotExist()
        compose.onNodeWithContentDescription("나 메시지 삭제").performClick()
        assertEquals(listOf("delete:101"), calls)
    }

    @Test
    fun `보내기는 보낼 수 있을 때만 켜진다(FR-208)`() {
        show(ChatUiState(draft = "   "))
        compose.onNodeWithContentDescription("보내기").assertIsNotEnabled()
    }

    @Test
    fun `글자를 넘기면 이유를 보이고 보내기를 막는다`() {
        show(ChatUiState(draft = "a".repeat(501)))
        compose.onNodeWithText("500자까지 보낼 수 있어요").assertExists()
        compose.onNodeWithContentDescription("보내기").assertIsNotEnabled()
    }

    @Test
    fun `내용이 있으면 보내기를 눌러 보낸다`() {
        show(ChatUiState(draft = "안녕"))
        compose.onNodeWithContentDescription("보내기").assertIsEnabled().performClick()
        assertEquals(listOf("send"), calls)
    }

    @Test
    fun `이전 쪽이 있으면 이전 메시지 더 보기 단추가 있다`() {
        show(ChatUiState(messages = listOf(chatMessage(51)), hasMore = true))
        compose.onNodeWithText("이전 메시지 더 보기").performClick()
        assertEquals("older", calls.last())
    }

    @Test
    fun `날이 바뀌는 곳에 날짜 줄이 있다`() {
        show(ChatUiState(messages = listOf(chatMessage(1, sentAt = T0.minusSeconds(86_400)), chatMessage(2))))
        assertEquals(2, compose.onAllNodes(hasText("월 ", substring = true)).fetchSemanticsNodes().size)
    }

    /** 메시지 내용으로 말풍선을 찾아 길게 누르기 동작(메시지 메뉴)을 연다. */
    private fun openMenu(content: String) {
        compose.onNode(
            hasContentDescription(content, substring = true) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
        ).performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun `채팅방 스크린샷 다크`() = capture(isDark = true)

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun `채팅방 스크린샷 라이트`() = capture(isDark = false)

    /** 라이트·다크 벌(RunkkTheme(darkTheme), NFR-041)로 그린다. */
    private fun capture(isDark: Boolean) {
        val name = if (isDark) "chat_room_dark" else "chat_room_light"
        compose.captureRunkkScreen(name, isDarkTheme = isDark) { ChatRoomScreen(sampleState(), ChatActions()) }
    }

    private fun sampleState() = ChatUiState(
        crewId = 5,
        hasMore = true,
        draft = "내일 7시에 봬요",
        messages = listOf(
            chatMessage(101, "오늘 저녁 한강 뛰실 분?", reactions = mapOf(ReactionKind.LIKE to 3)),
            chatMessage(102, "저요! 8시 괜찮아요", authorName = "새벽러너", reactions = mapOf(ReactionKind.HEART to 1)),
            chatMessage(103).removed(),
            chatMessage(
                104,
                "좋아요, 여의나루역에서 만나요",
                isMine = true,
                reactions = mapOf(ReactionKind.LIKE to 2),
                myReactions = setOf(ReactionKind.LIKE)
            ),
            chatMessage(
                null,
                "우비 챙기세요",
                isMine = true,
                sendState = ChatMessage.SendState.FAILED,
                clientMessageId = "c1"
            )
        )
    )
}
