package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModelStore
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.coach.CoachConversation
import com.a207.runkk.app.domain.coach.CoachEvent
import com.a207.runkk.app.domain.coach.CoachFailure
import com.a207.runkk.app.domain.coach.CoachRepository
import com.a207.runkk.app.ui.record.coach.CoachChatTurn
import com.a207.runkk.app.ui.record.coach.CoachChatViewModel
import java.io.IOException
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

/** FR-261~FR-263: 스트림·경곗값·재시도·취소 경로. */
@OptIn(ExperimentalCoroutinesApi::class)
class CoachChatViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeCoachRepository()

    @Test
    fun `답 조각은 한 답에 이어 붙이고 done으로 완료한다`() {
        val vm = CoachChatViewModel(repository)
        vm.send("어떻게 달릴까요?")
        assertEquals("c1", vm.state.value.conversationId)
        assertEquals(listOf(CoachChatTurn("어떻게 달릴까요?", "천천히 달려요", true)), vm.state.value.turns)
        assertFalse(vm.state.value.isSending)
        assertNull(vm.state.value.failure)
    }

    @Test
    fun `빈 질문과 공백과 501자는 전송하지 않고 500 코드 포인트는 전송한다`() {
        val vm = CoachChatViewModel(repository)
        listOf("", " \n ", "가".repeat(501), "😀".repeat(501)).forEach(vm::send)
        assertTrue(repository.questions.isEmpty())
        val question = "😀".repeat(500)
        vm.send(question)
        assertEquals(listOf<Pair<String?, String>>(null to question), repository.questions)
    }

    @Test
    fun `실패한 답은 다시 물을 때 질문을 중복 추가하지 않고 새 답으로 바꾼다`() {
        repository.events =
            flowOf(CoachEvent.Started("c1"), CoachEvent.Delta("일부 답"), CoachEvent.Failed(CoachFailure.RETRY))
        val vm = CoachChatViewModel(repository)
        vm.send("회복은 어떻게 하나요?")
        assertEquals(CoachFailure.RETRY, vm.state.value.failure)
        assertEquals("일부 답", vm.state.value.turns.single().answer)
        repository.events = flowOf(CoachEvent.Delta("쉬어 주세요"), CoachEvent.Done)
        vm.retry()
        assertEquals(listOf(null to "회복은 어떻게 하나요?", "c1" to "회복은 어떻게 하나요?"), repository.questions)
        assertEquals(listOf(CoachChatTurn("회복은 어떻게 하나요?", "쉬어 주세요", true)), vm.state.value.turns)
    }

    @Test
    fun `전송 중 연타를 무시하고 새 대화로 바꾸면 연결을 취소한다`() {
        var cancelled = false
        repository.events = flow {
            try {
                emit(CoachEvent.Started("c1"))
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val vm = CoachChatViewModel(repository)
        vm.send("첫 질문")
        vm.send("중복 질문")
        assertEquals(1, repository.questions.size)
        assertTrue(vm.state.value.isSending)
        vm.newConversation()
        assertTrue(cancelled)
        assertFalse(vm.state.value.isSending)
        assertNull(vm.state.value.conversationId)
        assertTrue(vm.state.value.turns.isEmpty())
        assertNull(vm.state.value.failure)
    }

    @Test
    fun `화면을 닫아 ViewModel이 취소되면 다시 묻기 오류로 바꾸지 않는다`() {
        var cancelled = false
        repository.events = flow {
            try {
                emit(CoachEvent.Started("c1"))
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val vm = CoachChatViewModel(repository)
        val store = ViewModelStore()
        store.put("coach", vm)
        vm.send("질문")
        assertTrue(vm.state.value.isSending)
        store.clear()
        assertTrue(cancelled)
        assertNull(vm.state.value.failure)
    }

    @Test
    fun `저장소 흐름만 취소되면 일부 답을 남기고 다시 물을 수 있다`() {
        repository.events = flow {
            emit(CoachEvent.Started("c1"))
            emit(CoachEvent.Delta("일부 답"))
            throw CancellationException("stream closed")
        }
        val vm = CoachChatViewModel(repository)
        vm.send("질문")
        assertFalse(vm.state.value.isSending)
        assertEquals(CoachFailure.RETRY, vm.state.value.failure)
        assertEquals(listOf(CoachChatTurn("질문", "일부 답")), vm.state.value.turns)
        repository.events = flowOf(CoachEvent.Delta("다시 받은 답"), CoachEvent.Done)
        vm.retry()
        assertEquals(listOf(null to "질문", "c1" to "질문"), repository.questions)
        assertEquals(listOf(CoachChatTurn("질문", "다시 받은 답", true)), vm.state.value.turns)
        assertFalse(vm.state.value.isSending)
        assertNull(vm.state.value.failure)
    }

    @Test
    fun `저장소 응답이 시간 초과되면 전송 상태를 풀고 다시 묻기를 안내한다`() = runTest(main.dispatcher) {
        repository.events = flow {
            emit(CoachEvent.Started("c1"))
            withTimeout(100) { awaitCancellation() }
        }
        val vm = CoachChatViewModel(repository)
        vm.send("질문")
        assertTrue(vm.state.value.isSending)
        advanceUntilIdle()
        assertFalse(vm.state.value.isSending)
        assertEquals(CoachFailure.RETRY, vm.state.value.failure)
        assertFalse(vm.state.value.turns.single().isComplete)
    }

    @Test
    fun `완료 사건 없이 끝나거나 예외가 나면 다시 묻기를 안내한다`() {
        listOf(
            flowOf(CoachEvent.Delta("일부 답")),
            flowOf(CoachEvent.Done),
            flow {
                throw IOException("offline")
            }
        ).forEach { events ->
            repository.events = events
            val vm = CoachChatViewModel(repository)
            vm.send("질문")
            assertEquals(CoachFailure.RETRY, vm.state.value.failure)
            assertFalse(vm.state.value.isSending)
            assertFalse(vm.state.value.turns.single().isComplete)
        }
    }

    @Test
    fun `동의와 한도와 만료 오류는 사유를 유지하고 자동으로 재전송하지 않는다`() {
        CoachFailure.entries.forEach { failure ->
            repository.events = flowOf(CoachEvent.Failed(failure))
            val vm = CoachChatViewModel(repository)
            val before = repository.questions.size
            vm.send("질문")
            assertEquals(failure, vm.state.value.failure)
            assertFalse(vm.state.value.isSending)
            if (failure != CoachFailure.RETRY) {
                vm.retry()
                assertEquals(before + 1, repository.questions.size)
            }
        }
    }

    @Test
    fun `대화 목록에서 선택한 ID로 질문을 이어 가며 새 대화는 ID 없이 보낸다`() {
        repository.conversationResult = Outcome.Success(listOf(conversation))
        val vm = CoachChatViewModel(repository)
        assertEquals(listOf(conversation), vm.state.value.conversations)
        vm.openConversation("old")
        vm.send("이어서 물어요")
        vm.newConversation()
        vm.send("새로 물어요")
        assertEquals(listOf("old" to "이어서 물어요", null to "새로 물어요"), repository.questions)
    }

    @Test
    fun `대화 목록 실패는 새 질문을 막지 않고 목록만 다시 읽을 수 있다`() {
        repository.conversationResult = Outcome.Failure(AppError.Network)
        val vm = CoachChatViewModel(repository)
        assertTrue(vm.state.value.conversationError != null)
        vm.send("새 질문")
        assertEquals(1, repository.questions.size)
        repository.conversationResult = Outcome.Success(listOf(conversation))
        vm.refreshConversations()
        assertNull(vm.state.value.conversationError)
        assertEquals(listOf(conversation), vm.state.value.conversations)
    }

    private val conversation = CoachConversation("old", "이전 질문", LocalDateTime.of(2026, 11, 2, 10, 0))
}

private class FakeCoachRepository : CoachRepository {
    val questions = mutableListOf<Pair<String?, String>>()
    var conversationResult: Outcome<List<CoachConversation>> = Outcome.Success(emptyList())
    var events: Flow<CoachEvent> = flowOf(
        CoachEvent.Started("c1"),
        CoachEvent.Delta("천천히 "),
        CoachEvent.Delta("달려요"),
        CoachEvent.Done
    )

    override fun ask(conversationId: String?, text: String): Flow<CoachEvent> {
        questions += conversationId to text
        return events
    }

    override suspend fun conversations(): Outcome<List<CoachConversation>> = conversationResult
}
