package com.a207.runkk.app.ui.record.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.coach.CoachEvent
import com.a207.runkk.app.domain.coach.CoachFailure
import com.a207.runkk.app.domain.coach.CoachRepository
import com.a207.runkk.app.ui.record.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** AI 코치 상태(D-453). 서버 중계 #241과 어댑터가 연결되기 전에는 제품 경로에 등록하지 않는다. */
class CoachChatViewModel(private val repository: CoachRepository) : ViewModel() {
    private val _state = MutableStateFlow(CoachChatUiState())
    val state = _state.asStateFlow()
    private var questionJob: Job? = null

    init {
        refreshConversations()
    }

    fun refreshConversations() {
        if (_state.value.isLoadingConversations) return
        _state.update { it.copy(isLoadingConversations = true, conversationError = null) }
        viewModelScope.launch {
            when (val result = repository.conversations()) {
                is Outcome.Success -> _state.update {
                    it.copy(isLoadingConversations = false, conversations = result.value)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoadingConversations = false, conversationError = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun send(text: String) {
        if (!_state.value.canAsk) return
        if (!CoachChatUiState.accepts(text)) {
            _state.update { it.copy(inputError = UiText.Res(R.string.record_chat_question_invalid)) }
            return
        }
        _state.update { it.copy(turns = it.turns + CoachChatTurn(text)) }
        ask(text)
    }

    fun retry() {
        val before = _state.value
        if (before.isSending || before.failure != CoachFailure.RETRY) return
        val last = before.turns.lastOrNull() ?: return
        _state.update { it.copy(turns = it.turns.dropLast(1) + last.copy(answer = "")) }
        ask(last.question)
    }

    fun openConversation(id: String) {
        if (_state.value.conversations.none { it.conversationId == id }) return
        newConversation()
        _state.update { it.copy(conversationId = id) }
    }

    fun newConversation() {
        questionJob?.cancel()
        questionJob = null
        _state.update {
            it.copy(conversationId = null, turns = emptyList(), isSending = false, failure = null, inputError = null)
        }
    }

    private fun ask(text: String) {
        val conversationId = _state.value.conversationId
        _state.update { it.copy(isSending = true, failure = null, inputError = null) }
        questionJob = viewModelScope.launch {
            try {
                repository.ask(conversationId, text).first { event ->
                    applyEvent(event)
                    event == CoachEvent.Done || event is CoachEvent.Failed
                }
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                // done 없이 끊긴 스트림도 완료로 보이면 안 된다.
                _state.update { it.copy(isSending = false, failure = CoachFailure.RETRY) }
            }
        }
    }

    private fun applyEvent(event: CoachEvent) {
        _state.update { current ->
            when (event) {
                is CoachEvent.Started -> current.copy(conversationId = event.conversationId)

                is CoachEvent.Delta -> current.copy(
                    turns = current.turns.dropLast(1) + current.turns.last().let {
                        it.copy(answer = it.answer + event.text)
                    }
                )

                CoachEvent.Done -> current.copy(
                    isSending = false,
                    failure = if (current.turns.last().answer.isBlank()) CoachFailure.RETRY else null,
                    turns = current.turns.dropLast(1) + current.turns.last().let {
                        it.copy(isComplete = it.answer.isNotBlank())
                    }
                )

                is CoachEvent.Failed -> current.copy(isSending = false, failure = event.reason)
            }
        }
    }
}
