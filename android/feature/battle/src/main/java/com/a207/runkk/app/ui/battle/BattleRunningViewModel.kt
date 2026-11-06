package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.battle.MyProgress
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.OpponentProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 대결 진행(FR-087~FR-091, FR-094, FR-098, FR-099, FR-102, FR-107, FR-182, FR-188). BattleRunCoordinator.state를 화면 상태로
 * 바꾼다. 측정·진행값 전송·추월 판정·도착·종료는 조정자가 하고, 내 거리·경과·페이스·경로선은 조정자 상태의 MyProgress로,
 * 상대 연결 상태는 Running.opponentConnection으로 받는다(ui.battle은 domain.run을 보지 않는다, 5.1.2).
 * 화면은 그만두기(짧게 누르면 안내, 길게 누르면 확인 뒤 forfeit, D-49)와 셀프런 카운트다운 "다시 고르기"(D-264)를 넘긴다.
 * 셀프런·셀럽런 그만두기는 조정자가 기록을 버리고 Discarded로 닫으며 결과 화면 없이 기록런 탭으로 돌아간다(D-266).
 */
@HiltViewModel
class BattleRunningViewModel @Inject constructor(
    private val coordinator: BattleRunCoordinator,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(BattleRunningUiState())
    val state: StateFlow<BattleRunningUiState> = _state.asStateFlow()

    private val _events = Channel<BattleRunningEvent>(Channel.BUFFERED)
    val events: Flow<BattleRunningEvent> = _events.receiveAsFlow()

    private var bound: Long? = null
    private var closesAt: Instant? = null
    private var countdownTo: Instant? = null
    private var sawRunning = false
    private var lastGuidanceSeen: BattleGuidance.Kind? = null
    private var tickJob: Job? = null
    private var guidanceJob: Job? = null
    private var hintJob: Job? = null
    private val bindJobs = mutableListOf<Job>()

    /**
     * 진행 화면 인자를 받아 조정자 상태 구독을 시작한다. 같은 대결로 다시 부르면 무시한다.
     * 조정자가 이 대결을 모르면(프로세스가 다시 떠 백 스택만 복원된 경우) [STALE_MILLIS] 뒤 실패 화면으로 바꿔 빠져나갈 수 있게 한다.
     */
    fun bind(key: Routes.BattleRunning) {
        if (bound == key.battleId) return
        bound = key.battleId
        bindJobs.forEach { it.cancel() }
        bindJobs.clear()
        tickJob?.cancel()
        closesAt = null
        countdownTo = null
        sawRunning = false
        lastGuidanceSeen = null
        _state.value = BattleRunningUiState(
            battleId = key.battleId,
            opponentName = key.opponentName,
            distance = Distance(key.distanceMeters),
            kind = OpponentKind.of(key.opponentKind),
            opponentRecord = key.opponentRecordSeconds?.let(Duration::ofSeconds),
            opponentRecordedOn = key.opponentRecordedOnEpochDay?.let(LocalDate::ofEpochDay),
            myBest = key.myBestSeconds?.let(Duration::ofSeconds),
            opponentGender = Gender.entries.firstOrNull { it.name == key.opponentGender },
            celebrityFigure = key.celebrityFigure,
            placementRound = key.placementRound
        )
        bindJobs += viewModelScope.launch { coordinator.state.collect(::onCoordinator) }
        bindJobs += viewModelScope.launch {
            // 폴링으로 이어진 동안은 메시지가 오가므로 끊김으로 보지 않는다.
            coordinator.liveState.collect { live ->
                _state.update { it.copy(isLiveLost = live == LiveState.CONNECTING || live == LiveState.CLOSED) }
            }
        }
        bindJobs += viewModelScope.launch {
            coordinator.failure.collect { error ->
                if (error != null) _state.update { it.copy(error = BattleErrorText.of(error)) }
            }
        }
        bindJobs += viewModelScope.launch {
            delay(STALE_MILLIS)
            _state.update {
                if (it.phase == BattlePhase.STARTING) {
                    it.copy(
                        phase = BattlePhase.FAILED,
                        error =
                            it.error ?: UiText.Res(R.string.battle_running_not_found)
                    )
                } else {
                    it
                }
            }
        }
    }

    /** 그만두기 짧게 누름: 길게 눌러야 한다는 안내만(D-49). */
    fun onForfeitTapped() = showHint(FORFEIT_HINT)

    /**
     * 그만두기 길게 누름·뒤로가기: 확인을 받는다(FR-094). 셀프런·셀럽런이 아직 출발 전(시작 대기·카운트다운)이면 그만두기(패)가 아니라
     * 취소 확인을 띄운다(D-324, D-337, 코드 리뷰 AN10-1): 확인하면 cancel → 기록 없이 Discarded.
     */
    fun onForfeitLongPressed() {
        val s = _state.value
        when {
            s.kind.isCancellable && (s.phase == BattlePhase.STARTING || s.phase == BattlePhase.COUNTDOWN) ->
                _state.update { it.copy(isConfirmingCancel = true) }

            s.phase == BattlePhase.RUNNING || s.phase == BattlePhase.CLOSING || s.phase == BattlePhase.COUNTDOWN ->
                _state.update { it.copy(isConfirmingForfeit = true) }
        }
    }

    fun onForfeitDismissed() = _state.update { it.copy(isConfirmingForfeit = false) }

    /** 셀프런·셀럽런 취소 버튼: 기록 없이 끝낼지 확인을 받는다. */
    fun onCancelTapped() {
        val s = _state.value
        if (s.kind.isCancellable && (s.phase == BattlePhase.RUNNING || s.phase == BattlePhase.CLOSING)) {
            _state.update { it.copy(isConfirmingCancel = true) }
        }
    }

    fun onCancelDismissed() = _state.update { it.copy(isConfirmingCancel = false) }

    /** 취소 확인: 서버는 대결을 취소하고 기기는 기록을 버린다(Discarded). */
    fun onCancelConfirmed() {
        _state.update { it.copy(isConfirmingCancel = false) }
        coordinator.cancel()
    }

    fun onForfeitConfirmed() {
        _state.update { it.copy(isConfirmingForfeit = false) }
        coordinator.forfeit()
    }

    /**
     * 셀프런 카운트다운 "다시 고르기"(D-264): 출발 전 취소라 서버는 대결을 취소하고(D-200) 조정자는 기록 없이 닫는다(Discarded).
     * 닫히면 기록런 탭으로 돌아간다.
     */
    fun onReselect() {
        val s = _state.value
        if (s.kind != OpponentKind.SELF || (s.phase != BattlePhase.COUNTDOWN && s.phase != BattlePhase.STARTING)) return
        coordinator.cancel()
    }

    /** 시작하지 못하고 끝난 화면을 닫을 때. */
    fun onFailureClosed() = coordinator.acknowledgeFinished()

    private fun onCoordinator(s: BattleRunState) {
        val battleId = _state.value.battleId
        when (s) {
            is BattleRunState.Countdown -> if (s.battleId == battleId) {
                countdownTo = s.startAt
                _state.update { it.copy(phase = BattlePhase.COUNTDOWN) }
                tick()
            }

            is BattleRunState.Running -> if (s.battleId == battleId) onRunning(s)

            is BattleRunState.Closing -> if (s.battleId == battleId) {
                closesAt = s.endsAt
                sawRunning = true
                val limit = Duration.between(clock.instant(), s.endsAt).seconds.coerceAtLeast(0)
                _state.update {
                    it.copy(
                        phase = BattlePhase.CLOSING,
                        arrivedName = s.arrivedName,
                        closingLimitSeconds = it.closingLimitSeconds ?: limit
                    ).withMine(s.mine)
                }
                tick()
            }

            is BattleRunState.Finished -> if (s.battleId == battleId) onFinished(battleId)

            is BattleRunState.Discarded -> if (s.battleId == battleId) onDiscarded()

            else -> Unit
        }
    }

    private fun onRunning(s: BattleRunState.Running) {
        sawRunning = true
        // 조정자는 마지막 안내를 계속 들고 있으므로 바뀐 때만 새 안내로 본다.
        val changed = s.lastGuidance != null && s.lastGuidance != lastGuidanceSeen
        if (changed) lastGuidanceSeen = s.lastGuidance
        val overtook = if (changed && s.lastGuidance == BattleGuidance.Kind.OVERTOOK) 1 else 0
        _state.update {
            it.copy(
                // 종료 카운트다운이 시작되면 조정자가 진행값을 계속 보내도 CLOSING을 유지한다(FR-107).
                phase = if (it.phase == BattlePhase.CLOSING) BattlePhase.CLOSING else BattlePhase.RUNNING,
                countdown = null,
                gapMeters = s.gapMeters ?: it.gapMeters,
                opponentDistance = s.opponentDistance ?: it.opponentDistance,
                connectionLabel = s.opponentConnection?.let(::connectionLabel) ?: it.connectionLabel,
                overtakes = it.overtakes + overtook
            ).withMine(s.mine)
        }
        if (changed) showGuidance(s.lastGuidance)
    }

    private fun onFinished(battleId: Long) {
        tickJob?.cancel()
        if (sawRunning) {
            _state.update { it.copy(phase = BattlePhase.FINISHED, isConfirmingForfeit = false) }
            _events.trySend(BattleRunningEvent.Finished(battleId))
        } else {
            _state.update {
                it.copy(
                    phase = BattlePhase.FAILED,
                    isConfirmingForfeit = false,
                    error = it.error ?: UiText.Res(R.string.battle_running_start_failed_permission)
                )
            }
        }
    }

    /** 기록을 버리고 닫혔다(D-266): 결과 화면 없이 기록런 탭으로 돌아가고 조정자를 다음 대결을 위해 비운다. */
    private fun onDiscarded() {
        tickJob?.cancel()
        _state.update { it.copy(phase = BattlePhase.FINISHED, isConfirmingForfeit = false) }
        coordinator.acknowledgeFinished()
        _events.trySend(BattleRunningEvent.Discarded)
    }

    private fun BattleRunningUiState.withMine(mine: MyProgress): BattleRunningUiState = copy(
        myDistance = mine.distance,
        elapsed = mine.elapsed,
        currentPace = mine.currentPace,
        averagePace = mine.averagePace,
        route = mine.route
    )

    /** 1초마다 시작 카운트다운·종료 카운트다운 남은 초를 갱신한다. 그 밖의 단계면 멈춘다. */
    private fun tick() {
        if (tickJob?.isActive == true) {
            refreshClock()
            return
        }
        tickJob = viewModelScope.launch {
            while (isActive) {
                refreshClock()
                val phase = _state.value.phase
                if (phase != BattlePhase.COUNTDOWN && phase != BattlePhase.CLOSING) break
                delay(TICK_MILLIS)
            }
        }
    }

    private fun refreshClock() {
        val now = clock.instant()
        _state.update { s ->
            s.copy(
                countdown = countdownTo?.takeIf {
                    s.phase == BattlePhase.COUNTDOWN
                }?.let { Duration.between(now, it).seconds.coerceAtLeast(0) },
                closingSeconds = closesAt?.takeIf { s.phase == BattlePhase.CLOSING }
                    ?.let { Duration.between(now, it).seconds.coerceAtLeast(0) }
            )
        }
    }

    private fun showGuidance(kind: BattleGuidance.Kind?) {
        _state.update { it.copy(guidance = kind) }
        guidanceJob?.cancel()
        guidanceJob = viewModelScope.launch {
            delay(GUIDANCE_MILLIS)
            _state.update { it.copy(guidance = null) }
        }
    }

    private fun showHint(text: UiText) {
        _state.update { it.copy(hint = text) }
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_MILLIS)
            _state.update { it.copy(hint = null) }
        }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val GUIDANCE_MILLIS = 5_000L
        const val HINT_MILLIS = 2_500L
        val FORFEIT_HINT = UiText.Res(R.string.battle_running_forfeit_hold)
        const val STALE_MILLIS = 5_000L
    }
}

/** 상대 연결 상태 문구(FR-088, 시안 HiBattleRunning "상대 연결됨"). */
internal fun connectionLabel(connection: OpponentProgress.Connection): UiText = when (connection) {
    OpponentProgress.Connection.CONNECTED -> UiText.Res(R.string.battle_running_connected)
    OpponentProgress.Connection.CHECKING -> UiText.Res(R.string.battle_running_checking)
}
