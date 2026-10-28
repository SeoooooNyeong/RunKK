package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 크루 만들기(FR-110): 만든 러너가 크루장이 된다. 만들면 크루 탭(내 크루 홈)으로. */
@HiltViewModel
class CrewCreateViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewCreateUiState())
    val state: StateFlow<CrewCreateUiState> = _state.asStateFlow()

    private val _events = Channel<CrewEvent>(Channel.BUFFERED)
    val events: Flow<CrewEvent> = _events.receiveAsFlow()

    fun onForm(form: CrewFormState) = _state.update { it.copy(form = form, error = null) }

    fun onSubmit() {
        val s = _state.value
        if (!s.form.isValid || s.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = crews.create(s.form.toForm())) {
                is Outcome.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.trySend(CrewEvent.Created)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isSaving = false, error = CrewErrorText.of(result.error))
                }
            }
        }
    }
}
