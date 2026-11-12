package com.a207.runkk.app.ui.record.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.coach.CoachEvent
import com.a207.runkk.app.domain.coach.CoachFailure
import com.a207.runkk.app.domain.coach.CoachRepository
import com.a207.runkk.app.ui.record.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** AI 코치 상태(D-453). 대화 목록 요청이 진입 시점의 서버 동의 검사도 맡는다. */
@HiltViewModel
class CoachChatViewModel @Inject constructor(private val repository: CoachRepository) : ViewModel() {
    private val _state = MutableStateFlow(CoachChatUiState())
    val state = _state.asStateFlow()
    private var questionJob: Job? = null
    private var historyJob: Job? = null

    init {
        refreshConversations()
    }

    fun refreshConversations() {
        if (_state.value.isLoadingConversations) return
        _state.update { it.copy(isLoadingConversations = true, conversationError = null) }
        viewModelScope.launch {
            when (val result = repository.conversations()) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoadingConversations = false,
                        conversations = result.value,
                        failure = it.failure?.takeUnless { failure -> failure.isGate() }
                    )
                }

                is Outcome.Failure -> {
                    val gate = gateOf(result.error)
                    if (gate != null) cancelJobs()
                    _state.update {
                        if (gate == null) {
                            it.copy(
                                isLoadingConversations = false,
                                conversationError = RunkkErrorText.of(result.error),
                                failure = it.failure?.takeUnless { failure -> failure.isGate() }
                            )
                        } else {
                            CoachChatUiState(failure = gate)
                        }
                    }
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
        if (!before.canAsk || before.failure != CoachFailure.RETRY) return
        val last = before.turns.lastOrNull() ?: return
        _state.update { it.copy(turns = it.turns.dropLast(1) + last.copy(answer = "")) }
        ask(last.question)
    }

    /** 이전 대화를 열면 그 대화의 지난 문답을 읽어 위에 그리고, 다 읽은 뒤 이어서 묻게 한다(S15P31A207-300). */
    fun openConversation(id: String) {
        if (_state.value.conversations.none { it.conversationId == id }) return
        newConversation()
        _state.update { it.copy(conversationId = id) }
        loadHistory(id)
    }

    /** 지난 문답 읽기가 실패했을 때 같은 대화를 다시 읽는다. */
    fun reloadHistory() {
        val current = _state.value
        val id = current.conversationId ?: return
        if (current.historyError == null || current.isLoadingHistory) return
        loadHistory(id)
    }

    fun newConversation() {
        cancelJobs()
        _state.update {
            val gate = it.failure?.takeIf { reason -> reason.isGate() }
            it.copy(
                conversationId = null,
                turns = emptyList(),
                isSending = false,
                failure = gate,
                inputError = null,
                history = emptyList(),
                isLoadingHistory = false,
                historyError = null
            )
        }
    }

    private fun loadHistory(id: String) {
        historyJob?.cancel()
        _state.update { it.copy(isLoadingHistory = true, historyError = null) }
        historyJob = viewModelScope.launch {
            val result = repository.messages(id)
            // 읽는 사이 다른 대화를 열었거나 새 대화로 바꿨으면 이 결과를 버린다.
            if (_state.value.conversationId != id) return@launch
            when (result) {
                is Outcome.Success -> _state.update {
                    it.copy(isLoadingHistory = false, history = result.value.toChatTurns())
                }

                is Outcome.Failure -> {
                    val gate = gateOf(result.error)
                    if (gate != null) questionJob?.cancel()
                    _state.update {
                        when {
                            gate != null -> CoachChatUiState(failure = gate)

                            // 목록을 읽은 뒤 보관 기한이 지났으면 새 대화를 열도록 안내한다.
                            (result.error as? AppError.Rejected)?.code == "CONVERSATION_EXPIRED" ->
                                it.copy(isLoadingHistory = false, failure = CoachFailure.EXPIRED)

                            else -> it.copy(isLoadingHistory = false, historyError = RunkkErrorText.of(result.error))
                        }
                    }
                }
            }
        }
    }

    private fun cancelJobs() {
        questionJob?.cancel()
        questionJob = null
        historyJob?.cancel()
        historyJob = null
    }

    private fun gateOf(error: AppError): CoachFailure? = when ((error as? AppError.Rejected)?.code) {
        "CONSENT_REQUIRED" -> CoachFailure.CONSENT_REQUIRED
        "FEATURE_CLOSED" -> CoachFailure.FEATURE_CLOSED
        else -> null
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

                is CoachEvent.Failed -> if (event.reason.isGate()) {
                    CoachChatUiState(failure = event.reason)
                } else {
                    current.copy(isSending = false, failure = event.reason)
                }
            }
        }
    }

    private fun CoachFailure.isGate() = this == CoachFailure.CONSENT_REQUIRED || this == CoachFailure.FEATURE_CLOSED
}
