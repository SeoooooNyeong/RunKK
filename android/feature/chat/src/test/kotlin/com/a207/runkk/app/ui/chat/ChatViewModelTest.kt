@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.chat

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ChatMessageKind
import com.a207.runkk.app.domain.chat.ChatPage
import com.a207.runkk.app.domain.chat.ChatRoom
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.chat.Reactor
import com.a207.runkk.app.domain.live.CrewChatPosted
import com.a207.runkk.app.domain.report.ReportReason
import com.a207.runkk.app.domain.report.ReportTarget
import com.a207.runkk.app.domain.report.ReportTargetKind
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** ChatViewModel(6.1.14 ui.chat): 시험 문서 2.2.16 TC-6486·TC-6495와 보내기 실패 재시도·리액션·삭제·신고·이전 쪽. */
class ChatViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val chats = FakeChatRepository()
    private val live = FakeLiveChannelProvider()
    private val registry = FakeOpenChatRoomRegistry()
    private val reports = FakeReportRepository()

    private fun TestScope.viewModel(vararg history: ChatPage): ChatViewModel {
        history.forEach { chats.historyResults.addLast(Outcome.Success(it)) }
        val room = ChatRoom(chats, live, registry, Clock.fixed(T0, ZoneOffset.UTC), backgroundScope)
        return ChatViewModel(room, chats, reports)
    }

    private fun ChatViewModel.shown(): ChatViewModel = also { it.onShown(CREW) }

    @Test
    fun `TC-6495 화면이 보이면 방을 열고 사라지면 닫는다 - 보임·사라짐·다시 보임`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onShown(CREW)
        vm.onHidden()
        vm.onShown(CREW)
        assertEquals(2, live.opens, "ChatRoom.open 2회(임대 2회)")
        assertEquals(1, live.channel.closes, "close 1회")
        assertEquals(listOf(CREW, CREW), registry.opened)
        assertEquals(listOf(CREW), registry.closed)
    }

    @Test
    fun `작성자_얼굴을_키마다_한_번_받는다`() = runTest(main.dispatcher) {
        chats.cachedFaces["selfie:9/z"] = listOf(FaceLayer(FacePart.HAIR, byteArrayOf(9)))
        val vm = viewModel(
            ChatPage(
                listOf(
                    chatMessage(101, authorFaceKey = "selfie:2/a"),
                    chatMessage(102, authorFaceKey = "selfie:2/a"),
                    chatMessage(103, authorFaceKey = "selfie:9/z"),
                    chatMessage(104, isMine = true, authorFaceKey = "selfie:1/me"),
                    chatMessage(105, authorFaceKey = null)
                ),
                hasMore = false
            )
        ).shown()
        live.channel.messages.emit(
            CrewChatPosted(CREW, 106, 2, "바람처럼", "또 왔어요", T0, null, authorFaceKey = "selfie:2/a")
        )
        live.channel.messages.emit(
            CrewChatPosted(CREW, 107, 3, "새벽곰", "안녕", T0, null, authorFaceKey = "selfie:3/c")
        )

        assertEquals(listOf<String?>("selfie:2/a", "selfie:3/c"), chats.faceCalls, "내 메시지와 받아 둔 그림은 받지 않는다")
        assertEquals(setOf("selfie:2/a", "selfie:3/c", "selfie:9/z"), vm.state.value.faces.keys)
    }

    @Test
    fun `얼굴_받기_실패는_오류를_띄우지_않는다`() = runTest(main.dispatcher) {
        chats.faceFailure = AppError.Network
        val vm = viewModel(ChatPage(listOf(chatMessage(101, authorFaceKey = "selfie:2/a")), hasMore = false)).shown()

        assertTrue(vm.state.value.faces.isEmpty())
        assertNull(vm.state.value.message, "얼굴 그림을 못 받아도 안내를 띄우지 않는다")

        chats.faceFailure = null
        live.channel.messages.emit(CrewChatPosted(CREW, 102, 3, "새벽곰", "안녕", T0, null))

        assertEquals(setOf("selfie:2/a"), vm.state.value.faces.keys, "다음 목록 갱신에 다시 받는다")
    }

    @Test
    fun `얼굴_받기가_계속_실패하면_세_번까지만_받는다`() = runTest(main.dispatcher) {
        chats.faceFailure = AppError.Network
        viewModel(ChatPage(listOf(chatMessage(101, authorFaceKey = "selfie:2/a")), hasMore = false)).shown()

        (102L..110L).forEach { id -> live.channel.messages.emit(CrewChatPosted(CREW, id, 3, "새벽곰", "안녕", T0, null)) }

        assertEquals(3, chats.faceCalls.size)
    }

    @Test
    fun `반응자_얼굴도_키마다_한_번_받는다`() = runTest(main.dispatcher) {
        chats.reactorsResult = Outcome.Success(
            listOf(
                Reactor(4, "느긋한고래", ReactionKind.CHECK, T0, isMe = false, faceKey = "selfie:4/d"),
                Reactor(4, "느긋한고래", ReactionKind.LIKE, T0, isMe = false, faceKey = "selfie:4/d"),
                Reactor(5, "새벽곰", ReactionKind.LIKE, T0, isMe = false, faceKey = null)
            )
        )
        val target = chatMessage(101, reactions = mapOf(ReactionKind.LIKE to 2, ReactionKind.CHECK to 1))
        val vm = viewModel(ChatPage(listOf(target), hasMore = false)).shown()

        vm.onOpenReactors(target, null)

        assertEquals(listOf<String?>("selfie:4/d"), chats.faceCalls)
        assertEquals(setOf("selfie:4/d"), vm.state.value.faces.keys)
    }

    @Test
    fun `TC-6486 보내기는 앞뒤 공백을 뺀 1~500자(코드 포인트)만 보낸다`() = runTest(main.dispatcher) {
        val cases =
            listOf(
                "" to 0,
                "a" to 1,
                "ab" to 1,
                "a".repeat(499) to 1,
                "a".repeat(500) to 1,
                "a".repeat(501) to 0,
                "   " to 0,
                "😀".repeat(500) to 1
            )
        cases.forEach { (text, expected) ->
            chats.posts.clear()
            val vm = viewModel().shown()
            vm.onDraft(text)
            vm.onSend()
            assertEquals(expected, chats.posts.size, "글자 수 ${text.codePointCount(0, text.length)}: '${text.take(3)}'")
            vm.onHidden()
        }
    }

    @Test
    fun `보내면 입력칸을 비우고 앞뒤 공백을 뺀 내용을 낙관적으로 목록에 넣는다(FR-208)`() = runTest(main.dispatcher) {
        val vm = viewModel(ChatPage(listOf(chatMessage(101)), hasMore = false)).shown()
        vm.onDraft("  안녕  ")
        assertTrue(vm.state.value.canSend)
        vm.onSend()
        assertEquals("", vm.state.value.draft)
        assertEquals("안녕", chats.posts.single().second)
        assertEquals(listOf("m101", "안녕"), vm.state.value.messages.map { it.content })
    }

    @Test
    fun `500자를 넘기면 보내기를 막고 넘었다고 알린다`() = runTest(main.dispatcher) {
        val vm = viewModel().shown()
        vm.onDraft("a".repeat(501))
        assertFalse(vm.state.value.canSend)
        assertTrue(vm.state.value.isOverLimit)
        assertEquals(501, vm.state.value.draftLength)
    }

    @Test
    fun `보내기 실패 항목은 FAILED로 남고 다시 보내기는 같은 clientMessageId로 보낸다(D-160 (3))`() = runTest(main.dispatcher) {
        chats.postResults.addLast(Outcome.Failure(AppError.Network))
        val vm = viewModel().shown()
        vm.onDraft("안녕")
        vm.onSend()
        val failed = vm.state.value.messages.single()
        assertEquals(ChatMessage.SendState.FAILED, failed.sendState)
        vm.onRetry(failed)
        assertEquals(2, chats.posts.size)
        assertEquals(chats.posts[0].first, chats.posts[1].first)
        assertEquals(ChatMessage.SendState.SENT, vm.state.value.messages.single().sendState)
    }

    @Test
    fun `리액션은 누르지 않은 종류면 달고 이미 단 종류면 뗀다(FR-210)`() = runTest(main.dispatcher) {
        val page =
            ChatPage(
                listOf(chatMessage(101), chatMessage(102, myReactions = setOf(ReactionKind.HEART))),
                hasMore = false
            )
        val vm = viewModel(page).shown()
        vm.onReaction(vm.state.value.messages[0], ReactionKind.LIKE)
        vm.onReaction(vm.state.value.messages[1], ReactionKind.HEART)
        assertEquals(listOf("react:101:LIKE", "unreact:102:HEART"), chats.reactions)
        assertEquals(setOf(ReactionKind.LIKE), vm.state.value.messages[0].myReactions)
    }

    @Test
    fun `지울 수 있는 메시지만 확인을 받고 지운다 - 지우면 자리표시(FR-213, D-160)`() = runTest(main.dispatcher) {
        val page = ChatPage(listOf(chatMessage(101, isMine = true), chatMessage(103)), hasMore = false)
        val vm = viewModel(page).shown()
        vm.onDeleteAsk(vm.state.value.messages[1])
        assertNull(vm.state.value.confirmDelete, "canDelete 거짓이면 묻지 않는다")
        vm.onDeleteAsk(vm.state.value.messages[0])
        assertEquals(101L, vm.state.value.confirmDelete?.messageId)
        vm.onDeleteConfirm()
        assertEquals(listOf(101L), chats.deleted)
        assertNull(vm.state.value.confirmDelete)
        assertTrue(vm.state.value.messages[0].isRemoved)
    }

    @Test
    fun `신고는 남의 메시지만 열고 사유를 골라야 보낼 수 있다 - 기타는 상세 1~200자(FR-214, D-149)`() = runTest(main.dispatcher) {
        val page = ChatPage(listOf(chatMessage(101, isMine = true), chatMessage(102)), hasMore = false)
        val vm = viewModel(page).shown()
        vm.onReportAsk(vm.state.value.messages[0])
        assertNull(vm.state.value.reportTarget, "내 메시지는 신고할 수 없다")
        vm.onReportAsk(vm.state.value.messages[1])
        assertEquals(102L, vm.state.value.reportTarget?.messageId)
        assertFalse(vm.state.value.canSubmitReport, "사유 없음")
        vm.onReportReason(ReportReason.OTHER)
        vm.onReportDetail("   ")
        assertFalse(vm.state.value.canSubmitReport, "기타는 상세가 필요하다")
        vm.onReportDetail("가".repeat(201))
        assertEquals(200, vm.state.value.reportDetail.length, "상세는 200자까지만 받는다")
        vm.onReportDetail(" 광고 링크를 계속 보내요 ")
        assertTrue(vm.state.value.canSubmitReport)
        vm.onReportSubmit()
        assertEquals(
            Triple(ReportTarget(ReportTargetKind.CHAT_MESSAGE, 102), ReportReason.OTHER, "광고 링크를 계속 보내요"),
            reports.calls.single()
        )
        assertNull(vm.state.value.reportTarget)
        assertEquals(UiText.Res(R.string.chat_report_done), vm.state.value.message)
    }

    @Test
    fun `기타가 아닌 사유는 상세 없이 보내고 이미 신고했으면 그 사유를 알린다`() = runTest(main.dispatcher) {
        reports.failure = AppError.Rejected("ALREADY_REPORTED")
        val vm = viewModel(ChatPage(listOf(chatMessage(102)), hasMore = false)).shown()
        vm.onReportAsk(vm.state.value.messages[0])
        vm.onReportReason(ReportReason.SPAM)
        vm.onReportSubmit()
        assertEquals(
            Triple(ReportTarget(ReportTargetKind.CHAT_MESSAGE, 102), ReportReason.SPAM, null),
            reports.calls.single()
        )
        assertEquals(UiText.Res(R.string.chat_error_already_reported), vm.state.value.message)
        assertNull(vm.state.value.reportTarget)
    }

    @Test
    fun `위로 끌면 이전 쪽을 읽는다(6·1·14 before)`() = runTest(main.dispatcher) {
        val vm = viewModel(ChatPage(listOf(chatMessage(51), chatMessage(52)), hasMore = true)).shown()
        chats.historyResults.addLast(Outcome.Success(ChatPage(listOf(chatMessage(50)), hasMore = false)))
        assertTrue(vm.state.value.hasMore)
        vm.onLoadOlder()
        assertEquals(Triple(51L, null, 50), chats.historyCalls.last())
        assertEquals(listOf(50L, 51L, 52L), vm.state.value.messages.map { it.messageId })
        assertFalse(vm.state.value.hasMore)
    }

    @Test
    fun `리액션·삭제·이전 쪽 읽기 실패는 안내 줄로 알린다 - 알려진 거부는 그 사유, 나머지는 동작별 문구(A4)`() = runTest(main.dispatcher) {
        val page = ChatPage(listOf(chatMessage(101, isMine = true), chatMessage(102)), hasMore = true)
        val vm = viewModel(page).shown()
        chats.reactFailure = AppError.Network
        vm.onReaction(vm.state.value.messages[1], ReactionKind.LIKE)
        assertEquals(UiText.Res(R.string.chat_react_failed), vm.state.value.message)
        vm.onMessageShown()
        chats.deleteFailure = AppError.Rejected("CHAT_MESSAGE_NOT_FOUND")
        vm.onDeleteAsk(vm.state.value.messages[0])
        vm.onDeleteConfirm()
        assertEquals(UiText.Res(R.string.chat_error_chat_message_not_found), vm.state.value.message)
        assertFalse(vm.state.value.messages[0].isRemoved)
        vm.onMessageShown()
        chats.historyResults.addLast(Outcome.Failure(AppError.Network))
        vm.onLoadOlder()
        assertEquals(UiText.Res(R.string.chat_load_older_failed), vm.state.value.message)
        assertTrue(vm.state.value.hasMore, "다시 끌어 읽을 수 있다")
        chats.deleteFailure = AppError.Server(500)
        vm.onMessageShown()
        vm.onDeleteAsk(vm.state.value.messages[0])
        vm.onDeleteConfirm()
        assertEquals(UiText.Res(R.string.chat_delete_failed), vm.state.value.message)
    }

    private companion object {
        const val CREW = 5L
    }

    @Test
    fun `W5 반응 알약을 길게 누르면 반응한 사람을 읽고 탭으로 거른다, 닫으면 비운다(F-09d)`() = runTest(main.dispatcher) {
        val like = Reactor(3, "새벽곰", ReactionKind.LIKE, T0, isMe = false)
        val check = Reactor(4, "나", ReactionKind.CHECK, T0, isMe = true)
        chats.reactorsResult = Outcome.Success(listOf(like, check))
        val vm = viewModel().shown()
        val message = chatMessage(101, reactions = mapOf(ReactionKind.LIKE to 1, ReactionKind.CHECK to 1))

        vm.onOpenReactors(message, ReactionKind.CHECK)

        assertEquals(listOf(101L), chats.reactorCalls)
        val opened = vm.state.value
        assertEquals(message, opened.reactorsTarget)
        assertEquals(ReactionKind.CHECK, opened.reactorsTab)
        assertFalse(opened.isLoadingReactors)
        assertEquals(listOf(check), opened.visibleReactors)
        assertEquals(listOf(ReactionKind.LIKE, ReactionKind.CHECK), opened.reactorTabs)

        vm.onReactorTab(null)
        assertEquals(listOf(like, check), vm.state.value.visibleReactors)

        vm.onCloseReactors()
        assertNull(vm.state.value.reactorsTarget)
        assertEquals(emptyList(), vm.state.value.reactors)
    }

    @Test
    fun `W5 반응한 사람 읽기에 실패하면 시트에 오류를 보이고, 지운 메시지·보내는 중 메시지는 열지 않는다`() = runTest(main.dispatcher) {
        chats.reactorsResult = Outcome.Failure(AppError.Network)
        val vm = viewModel().shown()

        vm.onOpenReactors(chatMessage(101, reactions = mapOf(ReactionKind.LIKE to 1)), null)
        assertTrue(vm.state.value.isReactorsFailed)
        assertFalse(vm.state.value.isLoadingReactors)

        vm.onCloseReactors()
        vm.onOpenReactors(chatMessage(102).removed(), null)
        vm.onOpenReactors(chatMessage(null, clientMessageId = "c-1"), null)
        assertNull(vm.state.value.reactorsTarget)
        assertEquals(listOf(101L), chats.reactorCalls)
    }

    @Test
    fun `W5 CHECK 반응도 달고 뗀다, 시스템 메시지는 신고하지 않는다`() = runTest(main.dispatcher) {
        val vm = viewModel().shown()
        vm.onReaction(chatMessage(101), ReactionKind.CHECK)
        vm.onReaction(chatMessage(101, myReactions = setOf(ReactionKind.CHECK)), ReactionKind.CHECK)
        assertEquals(listOf("react:101:CHECK", "unreact:101:CHECK"), chats.reactions)

        vm.onReportAsk(chatMessage(103, kind = ChatMessageKind.RUN_FINISHED))
        assertNull(vm.state.value.reportTarget)
    }
}
