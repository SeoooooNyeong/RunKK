package com.a207.runkk.app.ui.runimal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.RunimalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Runimal 16유형(G-5, FR-226, D-285): RunimalRepository.types로 16유형과 확정 이력을 보인다. */
@HiltViewModel
class RunimalTypesViewModel @Inject constructor(private val runimal: RunimalRepository) : ViewModel() {
    private val _state = MutableStateFlow(RunimalTypesUiState())
    val state: StateFlow<RunimalTypesUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val result = runimal.types()) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, board = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}
