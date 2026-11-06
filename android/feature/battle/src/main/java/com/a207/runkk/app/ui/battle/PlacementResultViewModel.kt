package com.a207.runkk.app.ui.battle

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.battle.BattleRecordUploads
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.PlacementRoundState
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

/** 성과 첫 줄 승패 문구(「승리」·「패배」·「무승부」). */
@StringRes
internal fun verdictLabelRes(verdict: Verdict): Int = when (verdict) {
    Verdict.WIN -> R.string.placement_verdict_win
    Verdict.LOSE -> R.string.placement_verdict_lose
    Verdict.DRAW -> R.string.placement_verdict_draw
}

/**
 * 배치고사 판 결과(FR-280, D-501): 대결 결과(BattleRepository.result)와 본인 기록 상세(RunDetailSource, D-283과 같은 길), 본인 배치
 * 진행(AccountRepository.placement)을 읽는다. 판 셈이 정해지지 않았으면(PENDING) 2초 간격으로 최대 15번(30초) 다시 읽고, 그래도 정해지지
 * 않으면 안내와 「다시 확인」을 보인다. 셈(COUNTED)이 정해지면 진행을 다시 읽어 배치고사가 끝났고 마지막 판이 이 대결이면 완료 화면으로
 * 바꿔 연다. 30초 뒤에도 정리 중이거나 진행을 끝내 읽지 못했으면 이 대결 기록이 올라갈 때(BattleRecordUploads)와 화면에 돌아올 때
 * 다시 읽는다(PL2-A2: 3판째여도 완료 화면에 닿을 길을 남긴다).
 */
@HiltViewModel
class PlacementResultViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val runDetails: RunDetailSource,
    private val account: AccountRepository,
    private val uploads: BattleRecordUploads
) : ViewModel() {
    private val _state = MutableStateFlow(PlacementResultUiState())
    val state: StateFlow<PlacementResultUiState> = _state.asStateFlow()

    private val _events = Channel<PlacementResultEvent>(Channel.BUFFERED)
    val events: Flow<PlacementResultEvent> = _events.receiveAsFlow()

    private var job: Job? = null
    private var detailJob: Job? = null
    private var uploadJob: Job? = null
    private var watchedBattleId: Long? = null

    /** 완료 화면으로 바꿔 열기를 이미 보냈다(다시 읽어도 한 번만). */
    private var isCompleteSent = false

    /** [battleId]의 판 결과를 읽는다. [isForced]면 같은 대결도 다시 읽는다(오류 뒤 다시 시도). */
    fun load(battleId: Long, isForced: Boolean = false) {
        if (!isForced && _state.value.battleId == battleId &&
            (job?.isActive == true || _state.value.result != null)
        ) {
            return
        }
        job?.cancel()
        // 같은 대결을 다시 읽을 때는 보이던 결과를 가리지 않는다.
        val hasResult = _state.value.battleId == battleId && _state.value.result != null
        _state.update {
            it.copy(
                isLoading = !hasResult,
                battleId = battleId,
                error = null,
                isStillPending = false,
                progressError = null
            )
        }
        job = viewModelScope.launch { read(battleId) }
        watchUploads(battleId)
    }

    /** 화면에 돌아올 때: 30초 뒤에도 정리 중이었거나 진행을 못 읽었으면 다시 읽는다(PL2-A2). */
    fun onResumed() {
        if (needsReread()) load(_state.value.battleId, isForced = true)
    }

    fun onNextRound() {
        _events.trySend(PlacementResultEvent.NextRound)
    }

    fun onHome() {
        _events.trySend(PlacementResultEvent.Home)
    }

    /** 왼쪽 위 뒤로·시스템 뒤로는 「홈으로」와 같다(D-501, R91). */
    fun onBack() = onHome()

    private suspend fun read(battleId: Long) {
        if (!show(battles.result(battleId))) return
        readProgress()
        var retries = 0
        while (_state.value.isPending && retries < PENDING_RETRIES) {
            delay(PENDING_RETRY_MILLIS)
            retries++
            if (show(battles.result(battleId)) && !_state.value.isPending) readProgress()
        }
        if (_state.value.isPending) _state.update { it.copy(isStillPending = true) }
    }

    /** 결과를 보인다. 읽었으면 true. 처음 읽기가 실패하면 오류를 보이고, 다시 읽기 실패는 앞 결과를 그대로 둔다. */
    private fun show(read: Outcome<BattleResult>): Boolean = when (read) {
        is Outcome.Success -> {
            val result = read.value
            _state.update { it.copy(isLoading = false, result = result, state = result.placementState, error = null) }
            result.recordId?.let(::readDetail)
            true
        }

        is Outcome.Failure -> {
            if (_state.value.result == null) {
                _state.update { it.copy(isLoading = false, error = BattleErrorText.of(read.error)) }
            }
            _state.value.result != null
        }
    }

    /** 이 대결 기록이 올라가면(미전송 목록에서 빠지면) 다시 읽을 차례인지 보고 다시 읽는다(BattleResultViewModel과 같은 길). */
    private fun watchUploads(battleId: Long) {
        if (uploadJob?.isActive == true && watchedBattleId == battleId) return
        uploadJob?.cancel()
        watchedBattleId = battleId
        uploadJob = viewModelScope.launch {
            uploads.settled(battleId).collect { if (needsReread()) load(battleId, isForced = true) }
        }
    }

    /** 다시 읽기가 멈췄고 30초 뒤에도 정리 중이거나 진행을 끝내 읽지 못한 상태인가. */
    private fun needsReread(): Boolean {
        val s = _state.value
        return job?.isActive != true && s.result != null && (s.isStillPending || s.progressError != null)
    }

    private fun readDetail(recordId: String) {
        if (_state.value.detail != null || detailJob?.isActive == true) return
        detailJob = viewModelScope.launch {
            (runDetails.detail(recordId) as? Outcome.Success)?.let { read ->
                _state.update { it.copy(detail = read.value) }
            }
        }
    }

    /**
     * 진행을 읽고, 이 판을 세어 배치고사가 끝났으면 완료 화면으로 바꿔 연다(TC-5881). 진행 읽기가 실패하면 [PROGRESS_RETRIES]번까지
     * 다시 읽고(3판째를 셌는데 한 번 실패로 완료 화면을 놓치지 않게), 그래도 실패하면 앞서 읽은 진행을 지우고(한 판 적게 보이지 않게)
     * 오류와 다시 시도를 보인다(PL2-A2).
     */
    private suspend fun readProgress() {
        var read = account.placement()
        var retries = 0
        while (read is Outcome.Failure && retries < PROGRESS_RETRIES) {
            delay(PENDING_RETRY_MILLIS)
            retries++
            read = account.placement()
        }
        val progress = (read as? Outcome.Success)?.value
        if (progress == null) {
            _state.update { it.copy(progress = null, progressError = UiText.Res(R.string.placement_load_failed)) }
            return
        }
        _state.update { it.copy(progress = progress, progressError = null) }
        val s = _state.value
        val isLastRound = progress.rounds.maxByOrNull { it.round }?.battleId == s.battleId
        val isDone =
            s.state == PlacementRoundState.COUNTED && progress.status == PlacementStatus.COMPLETED && isLastRound
        if (isDone && !isCompleteSent) {
            isCompleteSent = true
            _events.trySend(PlacementResultEvent.Replace(Routes.Key(Routes.PLACEMENT_COMPLETE)))
        }
    }

    internal companion object {
        /** 판 셈 정리를 다시 읽는 간격과 횟수(2초 × 15 = 30초, D-501). */
        const val PENDING_RETRY_MILLIS = 2_000L
        const val PENDING_RETRIES = 15

        /** 진행 읽기 실패를 다시 읽는 횟수. */
        const val PROGRESS_RETRIES = 2
    }
}
