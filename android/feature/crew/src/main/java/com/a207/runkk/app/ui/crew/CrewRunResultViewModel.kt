package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
import com.a207.runkk.app.domain.crew.CrewRunResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루런 결과(합산 방식, F-16·F-16b): 크루 합산 거리와 참가자별 거리, 내 기록 상세. 아직 달리는 크루원이 있거나(RUNNING) 내 기록이 아직
 * 올라가지 않았으면(myRecordId 없음) [REREAD_MILLIS] 간격으로 [REREADS]번까지 다시 읽는다. 내 기록 ID가 생기면 상세(GET /runs/{id})를
 * 한 번 읽는다.
 */
@HiltViewModel
class CrewRunResultViewModel @Inject constructor(
    private val crews: CrewRepository,
    private val coordinator: CrewRunCoordinator,
    private val details: CrewRunDetailSource
) : ViewModel() {
    private val _state = MutableStateFlow(CrewRunResultUiState())
    val state: StateFlow<CrewRunResultUiState> = _state.asStateFlow()
    private var loaded: Long? = null
    private var job: Job? = null
    private var detailJob: Job? = null

    fun load(postId: Long, isForced: Boolean = false) {
        if (!isForced && loaded == postId) return
        loaded = postId
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            var rereads = 0
            while (true) {
                when (val r = crews.crewRunResult(postId)) {
                    is Outcome.Success -> {
                        _state.update { it.copy(isLoading = false, result = r.value, error = null) }
                        coordinator.acknowledgeFinished()
                        readDetail(r.value.myRecordId)
                        if (!r.value.needsReread() || rereads >= REREADS) return@launch
                    }

                    is Outcome.Failure -> {
                        if (_state.value.result == null) {
                            loaded = null
                            _state.update { it.copy(isLoading = false, error = CrewErrorText.of(r.error)) }
                        }
                        return@launch
                    }
                }
                rereads++
                delay(REREAD_MILLIS)
            }
        }
    }

    private fun readDetail(recordId: String?) {
        if (recordId == null || _state.value.detail != null || detailJob?.isActive == true) return
        detailJob = viewModelScope.launch {
            (details.detail(recordId) as? Outcome.Success)?.let { read ->
                _state.update { it.copy(detail = read.value) }
            }
        }
    }

    /** 아직 달리는 크루원이 있거나, 내가 참가자인데 내 기록이 아직 올라가지 않았다. */
    private fun CrewRunResult.needsReread(): Boolean = isRunning || (myRecordId == null && participants.any { it.isMe })

    internal companion object {
        const val REREAD_MILLIS = 3_000L
        const val REREADS = 10
    }
}
