package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.PollForm
import com.a207.runkk.app.domain.crew.PollMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 투표 만들기(FR-206). 길이·개수는 화면이 막고, 마감 시각은 보낼 때 지금과 비교한다. 최종 판정은 서버(PollDraft, POLL_INVALID). */
@HiltViewModel
class CrewPollWriteViewModel @Inject constructor(private val crews: CrewRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(CrewPollWriteUiState(today = LocalDate.now(clock)))
    val state: StateFlow<CrewPollWriteUiState> = _state.asStateFlow()

    fun onQuestion(text: String) =
        _state.update { it.copy(question = text.takeCodePoints(CrewPollWriteUiState.QUESTION_MAX), error = null) }

    fun onMethod(method: PollMethod) = _state.update { it.copy(method = method) }

    fun onOption(index: Int, text: String) = _state.update { s ->
        if (index !in s.options.indices) {
            s
        } else {
            val options = s.options.toMutableList()
            options[index] = text.takeCodePoints(CrewPollWriteUiState.OPTION_MAX)
            s.copy(options = options)
        }
    }

    fun onAddOption() = _state.update { if (it.canAddOption) it.copy(options = it.options + "") else it }

    fun onRemoveOption(index: Int) = _state.update { s ->
        if (!s.canRemoveOption || index !in s.options.indices) {
            s
        } else {
            s.copy(options = s.options.filterIndexed { i, _ -> i != index })
        }
    }

    fun onDay(offset: Int) =
        _state.update { it.copy(dayOffset = offset.coerceIn(0, CrewPollWriteUiState.DAYS - 1), error = null) }

    fun onTime(time: LocalTime) = _state.update { it.copy(time = time, error = null) }

    fun onSubmit(crewId: Long) {
        val s = _state.value
        if (!s.isValid || s.isSaving) return
        val closesAt = s.today.plusDays(s.dayOffset.toLong()).atTime(s.time).atZone(clock.zone).toInstant()
        if (!closesAt.isAfter(clock.instant())) {
            _state.update { it.copy(error = uiText(R.string.crew_poll_write_past_time)) }
            return
        }
        val options = if (s.method == PollMethod.YES_NO) emptyList() else s.options.map { it.trim() }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val r = crews.writePoll(crewId, PollForm(s.question.trim(), s.method, options, closesAt))) {
                is Outcome.Success -> _state.update { it.copy(isSaving = false, isPosted = true) }
                is Outcome.Failure -> _state.update { it.copy(isSaving = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }
}
