package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.account.myAvatar
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
 * 본인 기록 상세(RunDetailSource)를 2쪽에 보인다. 대결을 끝낸 직후 연 결과에 티어 평가가 실리면 평가 화면을 한 번 띄운다(D-284).
 * 기록 대결 탭·알림함에서 다시 연 결과는 평가가 실려 있어도 띄우지 않는다(U8-6).
 * 결과를 보인 뒤 BattleRunCoordinator.acknowledgeFinished를 불러 다음 대결 화면이 지난 회의 종료 상태를 받지 않게 한다.
 * 그만두기·오프라인 종료 뒤에는 내 기록이 아직 서버에 없어 비교표 없는 결과나 네트워크 오류가 먼저 온다. 이 대결 기록이 올라가면
 * (BattleRecordUploads) 다시 읽고, 네트워크 오류는 몇 초마다 다시 읽는다(배틀 QA 5번).
 */
@HiltViewModel
class BattleResultViewModel internal constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val uploads: BattleRecordUploads,
    private val runDetails: RunDetailSource,
    /** 내 아바타(홈과 같은 출처, AccountRepository.myAvatar). 결과 쪽 내 그림과 배경에 쓴다. */
    private val myAvatar: suspend () -> MyAvatar? = { null },
    /** 첫 그림의 내 아바타(기기에 남긴 마지막 모습, 없으면 null). */
    lastAvatar: () -> MyAvatar? = { null }
) : ViewModel() {
    @Inject
    constructor(
        battles: BattleRepository,
        coordinator: BattleRunCoordinator,
        uploads: BattleRecordUploads,
        runDetails: RunDetailSource,
        account: AccountRepository
    ) : this(battles, coordinator, uploads, runDetails, { account.myAvatar() }, account::lastMyAvatar)

    // 기기에 남긴 마지막 내 아바타(AccountRepository.lastMyAvatar)를 첫 값으로 그려 기본 아바타가 보였다가 바뀌지 않게 한다.
    private val _state = MutableStateFlow(BattleResultUiState(myAvatar = lastAvatar()))
    private var isAvatarRead = false
    val state: StateFlow<BattleResultUiState> = _state.asStateFlow()

    private val _events = Channel<BattleResultEvent>(Channel.BUFFERED)
    val events: Flow<BattleResultEvent> = _events.receiveAsFlow()

    /** 이 화면이 평가 화면을 띄울 차례인가: 끝낸 직후 진입이고 아직 띄우지 않았다(한 번만, D-284, U8-6). */
    private var isEvaluationDue = false

    /** 막 끝난 배치고사 판이면 판 결과로 바꿔 열 차례인가(한 번만, D-501). 이력에서 연 옛 판은 기존 결과다. */
    private var isPlacementDue = false

    /** 판 결과로 바꿔 열었다. 이 화면의 다시 읽기를 멈춘다. */
    private var isPlacementOpened = false
    private var detailJob: Job? = null
    private var job: Job? = null
    private var uploadJob: Job? = null
    private var avatarJob: Job? = null
    private var lastError: AppError? = null

    /**
     * [battleId]의 결과를 읽는다. [opensEvaluation]은 대결을 끝낸 직후의 첫 읽기에서만 참으로 넘긴다(그 결과에 평가가 실리면 평가 화면).
     * [isJustFinished]는 대결을 끝낸 직후 진입이고, 그 결과가 배치고사 판이면 판 결과 화면으로 바꿔 연다(D-501).
     * [isForced]면 같은 대결도 다시 읽는다(다시 읽기는 평가 화면 여부를 바꾸지 않는다).
     */
    fun load(
        battleId: Long,
        opensEvaluation: Boolean = false,
        isForced: Boolean = false,
        isJustFinished: Boolean = false
    ) {
        if (_state.value.battleId == null) {
            if (opensEvaluation) isEvaluationDue = true
            if (isJustFinished) isPlacementDue = true
        }
        if (isPlacementOpened) return
        if (!isForced && _state.value.battleId == battleId && _state.value.error == null) return
        job?.cancel()
        _state.update { it.copy(isLoading = true, battleId = battleId, error = null) }
        job = viewModelScope.launch { read(battleId) }
        watchUploads(battleId)
        readMyAvatar()
    }

    /** 내 아바타(홈과 같은 얼굴·배경)를 한 번 읽는다. 못 읽으면 기본 아바타 그대로다. */
    private fun readMyAvatar() {
        if (isAvatarRead || avatarJob?.isActive == true) return
        avatarJob = viewModelScope.launch {
            myAvatar()?.let { look ->
                isAvatarRead = true
                _state.update { it.copy(myAvatar = look) }
            }
        }
    }

    /** 읽고, 네트워크 오류면 몇 초마다, 보류면 판정이 나올 때까지 다시 읽는다. */
    private suspend fun read(battleId: Long) {
        show(battles.result(battleId))
        // 막 끝난 배치고사 판은 판 결과 화면이 다시 읽는다(D-501): 이 화면은 더 읽지 않는다.
        if (isPlacementOpened) return
        var offlineRetries = 0
        while (_state.value.result == null && lastError.isTransient() && offlineRetries < OFFLINE_RETRIES) {
            delay(OFFLINE_RETRY_MILLIS)
            show(battles.result(battleId))
            offlineRetries++
        }
        if (isPlacementOpened) return
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
                if (openPlacementIfDue(result.value)) return
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
        if (evaluation != null && isEvaluationDue) {
            isEvaluationDue = false
            _events.trySend(BattleResultEvent.NavigateEvaluation(evaluation))
        }
    }

    /** 막 끝난 배치고사 판이면(판정 전 보류여도) 판 결과로 바꿔 열고 이 화면의 다시 읽기를 멈춘다. 열었으면 true. */
    private fun openPlacementIfDue(result: BattleResult): Boolean {
        if (!isPlacementDue || result.placementRound == null) return false
        isPlacementDue = false
        isEvaluationDue = false
        isPlacementOpened = true
        _events.trySend(BattleResultEvent.OpenPlacementResult(result.battleId))
        uploadJob?.cancel()
        return true
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
