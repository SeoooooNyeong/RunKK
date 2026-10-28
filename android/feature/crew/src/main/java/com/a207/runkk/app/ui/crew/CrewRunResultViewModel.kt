package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 크루런 결과(FR-191): 완주 인원, 순위별 기록·평균 페이스, 첫 크루런, 크루 주간 거리 반영. */
@HiltViewModel
class CrewRunResultViewModel @Inject constructor(
    private val crews: CrewRepository,
    private val coordinator: CrewRunCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(CrewRunResultUiState())
    val state: StateFlow<CrewRunResultUiState> = _state.asStateFlow()
    private var loaded: Long? = null
    private var job: Job? = null

    fun load(postId: Long, isForced: Boolean = false) {
        if (!isForced && loaded == postId) return
        loaded = postId
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val r = crews.crewRunResult(postId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isLoading = false, result = r.value) }
                    coordinator.acknowledgeFinished()
                }

                is Outcome.Failure -> {
                    loaded = null
                    _state.update { it.copy(isLoading = false, error = CrewErrorText.of(r.error)) }
                }
            }
        }
    }
}
