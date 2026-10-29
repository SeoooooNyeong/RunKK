package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleRecordUploads
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 대결 결과(FR-093·FR-103·FR-108, D-283): BattleRepository.result 판정(상대 표시명·도달 시간 차)·셀프런 비교표·적립 합계를 1쪽에,
 * 본인 기록 상세(RunDetailSource)를 2쪽에 보인다. 결과에 티어 평가가 실리면 평가 화면을 한 번 띄운다(D-284).
 * 결과를 보인 뒤 BattleRunCoordinator.acknowledgeFinished를 불러 다음 대결 화면이 지난 회의 종료 상태를 받지 않게 한다.
 * 그만두기·오프라인 종료 뒤에는 내 기록이 아직 서버에 없어 비교표 없는 결과나 네트워크 오류가 먼저 온다. 이 대결 기록이 올라가면
 * (BattleRecordUploads) 다시 읽고, 네트워크 오류는 몇 초마다 다시 읽는다(배틀 QA 5번).
 */
@HiltViewModel
class BattleResultViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val uploads: BattleRecordUploads,
    private val runDetails: RunDetailSource
) : ViewModel() {
    private val _state = MutableStateFlow(BattleResultUiState())
    val state: StateFlow<BattleResultUiState> = _state.asStateFlow()

    private val _events = Channel<BattleResultEvent>(Channel.BUFFERED)
    val events: Flow<BattleResultEvent> = _events.receiveAsFlow()

    /** 평가 화면을 이미 띄웠는가(한 번만, D-284). */
    private var isEvaluationShown = false
    private var detailJob: Job? = null
    private var job: Job? = null
    private var uploadJob: Job? = null
    private var lastError: AppError? = null

    fun load(battleId: Long, isForced: Boolean = false) {
        if (!isForced && _state.value.battleId == battleId && _state.value.error == null) return
        job?.cancel()
        _state.update { it.copy(isLoading = true, battleId = battleId, error = null) }
        job = viewModelScope.launch { read(battleId) }
        watchUploads(battleId)
    }

    /** 읽고, 네트워크 오류면 몇 초마다, 보류면 판정이 나올 때까지 다시 읽는다. */
    private suspend fun read(battleId: Long) {
        show(battles.result(battleId))
        var offlineRetries = 0
        while (_state.value.result == null && lastError.isTransient() && offlineRetries < OFFLINE_RETRIES) {
            delay(OFFLINE_RETRY_MILLIS)
            show(battles.result(battleId))
            offlineRetries++
        }
        // 도착 직후에는 기록이 아직 올라가지 않아 보류로 온다. 서버는 판정을 앱에 알리지 않으므로 판정이 나올 때까지 다시 읽는다.
        // 다시 읽는 동안은 결과를 확인하는 화면을 보이고, 다시 읽기가 실패하면 그 화면을 그대로 둔다.
        try {
            repeat(PENDING_RETRIES) {
                if (_state.value.result?.isPending != true) return
                _state.update { it.copy(isAwaitingVerdict = true) }
                delay(PENDING_RETRY_MILLIS)
                (battles.result(battleId) as? Outcome.Success)?.let(::show)
            }
        } finally {
            _state.update { it.copy(isAwaitingVerdict = false) }
        }
    }

    /**
     * 이 대결 기록이 올라가면(미전송 목록에서 빠지면) 결과가 아직 덜 찼을 때 다시 읽는다. 기다리던 다시 읽기(네트워크 오류·보류)는
     * 멈추고 바로 읽는다. 첫 읽기가 서버에 가 있는 동안은 그 응답을 기다린다.
     */
    private fun watchUploads(battleId: Long) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            uploads.settled(battleId).collect {
                if (_state.value.isLoading || !needsReread()) return@collect
                job?.cancel()
                job = viewModelScope.launch { read(battleId) }
            }
        }
    }

    /** 네트워크 오류로 읽지 못했거나, 보류이거나, 내 기록이 빠진 결과(비교표가 비어 있음)인가. */
    private fun needsReread(): Boolean {
        val result = _state.value.result ?: return lastError.isTransient()
        return result.isPending || result.recordId == null
    }

    private fun show(result: Outcome<BattleResult>) {
        when (result) {
            is Outcome.Success -> {
                lastError = null
                _state.update { it.copy(isLoading = false, result = result.value, error = null) }
                coordinator.acknowledgeFinished()
                onResult(result.value)
            }

            is Outcome.Failure -> {
                lastError = result.error
                _state.update { it.copy(isLoading = false, error = BattleErrorText.of(result.error)) }
            }
        }
    }

    /**
     * 판정이 나온 결과: 본인 기록이 있으면 2쪽 상세를 읽고(한 번), 그 기록이 티어 평가를 일으켰으면 평가 화면을 한 번 띄운다(D-284).
     */
    private fun onResult(result: BattleResult) {
        if (result.isPending) return
        val recordId = result.recordId
        if (recordId != null && _state.value.detail == null && detailJob?.isActive != true) {
            detailJob = viewModelScope.launch {
                (runDetails.detail(recordId) as? Outcome.Success)?.let { read ->
                    _state.update { it.copy(detail = read.value) }
                }
            }
        }
        val evaluation = result.evaluation
        if (evaluation != null && !isEvaluationShown) {
            isEvaluationShown = true
            _events.trySend(BattleResultEvent.NavigateEvaluation(evaluation))
        }
    }

    /** 잠시 뒤 다시 읽으면 될 실패(연결 실패·서버 오류). 거부는 다시 읽어도 같다. */
    private fun AppError?.isTransient(): Boolean = this is AppError.Network || this is AppError.Server

    internal companion object {
        /** 보류 결과를 다시 읽는 간격과 횟수(3초 × 20 = 1분). 그 뒤에는 결과 다시 보기로 읽는다. */
        const val PENDING_RETRY_MILLIS = 3_000L
        const val PENDING_RETRIES = 20

        /** 네트워크 오류를 다시 읽는 간격과 횟수(5초 × 24 = 2분). 그 뒤에는 기록이 올라갈 때나 다시 시도로 읽는다. */
        const val OFFLINE_RETRY_MILLIS = 5_000L
        const val OFFLINE_RETRIES = 24
    }
}
