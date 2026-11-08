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
 * 크루런 결과(합산 방식, F-16·F-16b): 크루 합산 거리와 참가자별 거리, 내 기록 상세. 내 기록 ID가 생기면 상세(GET /runs/{id})를 한 번 읽는다.
 *
 * 다시 읽기(MR !91 AN-3): 아직 달리는 크루원이 있으면(RUNNING) 크루런이 닫힐 때까지 읽되 처음 [REREADS]번은 [REREAD_MILLIS] 간격, 그
 * 뒤로는 간격을 두 배씩 늘려 [MAX_REREAD_MILLIS]까지 둔다(배터리). 크루런은 거리 × 15분 뒤 서버가 닫지만 그래도 [RUNNING_WATCH_MILLIS]를
 * 넘기면 멈춘다. 크루런은 닫혔고 내 기록만 아직이면(myRecordId 없음) [REREAD_MILLIS] 간격으로 [REREADS]번까지만 읽는다. 읽기가 실패해도
 * 이미 받은 결과가 있으면 같은 규칙으로 이어 간다.
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
            var waited = 0L
            while (true) {
                val last = read(postId) ?: return@launch
                if (!last.needsReread(rereads, waited)) return@launch
                val pause = rereadDelay(rereads)
                rereads++
                waited += pause
                delay(pause)
            }
        }
    }

    /** 결과를 한 번 읽어 화면에 반영하고, 지금 보이는 결과를 돌려준다. 처음 읽기부터 실패하면 오류를 보이고 null. */
    private suspend fun read(postId: Long): CrewRunResult? {
        when (val r = crews.crewRunResult(postId)) {
            is Outcome.Success -> {
                _state.update { it.copy(isLoading = false, result = r.value, error = null) }
                coordinator.acknowledgeFinished()
                readDetail(r.value.myRecordId)
            }

            is Outcome.Failure -> {
                if (_state.value.result == null) {
                    loaded = null
                    _state.update { it.copy(isLoading = false, error = CrewErrorText.of(r.error)) }
                }
            }
        }
        return _state.value.result
    }

    private fun readDetail(recordId: String?) {
        if (recordId == null || _state.value.detail != null || detailJob?.isActive == true) return
        detailJob = viewModelScope.launch {
            (details.detail(recordId) as? Outcome.Success)?.let { read ->
                _state.update { it.copy(detail = read.value) }
            }
        }
    }

    /** 아직 달리는 크루원이 있으면 상한 시간까지, 내가 참가자인데 내 기록이 아직이면 [REREADS]번까지. */
    private fun CrewRunResult.needsReread(rereads: Int, waited: Long): Boolean = when {
        isRunning -> waited < RUNNING_WATCH_MILLIS
        myRecordId == null && participants.any { it.isMe } -> rereads < REREADS
        else -> false
    }

    internal companion object {
        const val REREAD_MILLIS = 3_000L
        const val REREADS = 10

        /** 늘린 간격의 상한. */
        const val MAX_REREAD_MILLIS = 60_000L

        /** 진행 중 결과를 지켜보는 상한(가장 긴 10km 크루런 150분 + 여유). */
        const val RUNNING_WATCH_MILLIS = 3 * 60 * 60_000L

        private const val MAX_DOUBLINGS = 5

        /** [reread]번째 다시 읽기 전 기다림: 처음 [REREADS]번은 [REREAD_MILLIS], 그 뒤 6·12·24·48초, 이후 [MAX_REREAD_MILLIS]. */
        fun rereadDelay(reread: Int): Long {
            if (reread < REREADS) return REREAD_MILLIS
            val doublings = (reread - REREADS + 1).coerceAtMost(MAX_DOUBLINGS)
            return (REREAD_MILLIS shl doublings).coerceAtMost(MAX_REREAD_MILLIS)
        }
    }
}
