package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.live.BattleGuidance
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
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

/** 대결 진행 단계: 시작 준비 → 카운트다운 → 측정 → 종료 카운트다운 → 끝. 시작하지 못하고 끝나면 FAILED. */
enum class BattlePhase { STARTING, COUNTDOWN, RUNNING, CLOSING, FINISHED, FAILED }

data class BattleRunningUiState(
    val battleId: Long = 0,
    val opponentName: String = "",
    val distance: Distance = Distance(DEFAULT_METERS),
    val kind: OpponentKind = OpponentKind.RANDOM,
    val phase: BattlePhase = BattlePhase.STARTING,
    /** 측정 시작까지 남은 초(COUNTDOWN). */
    val countdown: Long? = null,
    /** 나 − 상대(m), 앞서면 양수. 첫 진행값 전이면 null. */
    val gapMeters: Int? = null,
    val opponentDistance: Distance? = null,
    val elapsed: Duration = Duration.ZERO,
    /** 방금 바뀐 앞뒤 순서 안내(잠깐 보인다, FR-188). */
    val guidance: BattleGuidance.Kind? = null,
    /** 이 화면에서 본 추월 횟수. 공식 횟수는 결과 비교표(서버)다. */
    val overtakes: Int = 0,
    /** 먼저 도착한 사람(종료 카운트다운, FR-107). */
    val arrivedName: String? = null,
    val closingSeconds: Long? = null,
    val confirmingForfeit: Boolean = false,
    val hint: String? = null,
    val error: String? = null
) {
    /** 내 누적 거리 = 상대 거리 + 격차(조정자 상태에 내 거리가 따로 없다). */
    val myDistance: Distance
        get() {
            val theirs = opponentDistance ?: return Distance.ZERO
            val gap = gapMeters ?: return Distance.ZERO
            return Distance((theirs.meters + gap).coerceAtLeast(0))
        }

    val averagePace: Pace?
        get() = myDistance.takeIf { it.meters > 0 && !elapsed.isZero }?.let { Pace.of(elapsed, it) }

    private companion object {
        const val DEFAULT_METERS = 5_000L
    }
}

sealed interface BattleRunningEvent {
    data class Finished(val battleId: Long) : BattleRunningEvent
}

/**
 * 대결 진행(FR-087~FR-091, FR-094, FR-098, FR-099, FR-102, FR-107, FR-188). BattleRunCoordinator.state를 화면 상태로 바꾼다.
 * 측정·진행값 전송·추월 판정·도착·종료는 조정자가 하고, 화면은 그만두기(확인 뒤 forfeit, D-49 길게 누름)만 넘긴다.
 * 상대 연결 상태("연결 확인 중")와 내 측정값(RunProgress)은 ui.battle이 볼 수 있는 상태에 없어 격차·상대 거리로 보인다.
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
    private var startedAt: Instant? = null
    private var closesAt: Instant? = null
    private var countdownTo: Instant? = null
    private var sawRunning = false
    private var lastGuidanceSeen: BattleGuidance.Kind? = null
    private var tickJob: Job? = null
    private var guidanceJob: Job? = null
    private var hintJob: Job? = null

    /** 진행 화면 인자를 받아 조정자 상태 구독을 시작한다. 같은 대결로 다시 부르면 무시한다. */
    fun bind(key: Routes.BattleRunning) {
        if (bound == key.battleId) return
        bound = key.battleId
        _state.value = BattleRunningUiState(
            battleId = key.battleId,
            opponentName = key.opponentName,
            distance = Distance(key.distanceMeters),
            kind = OpponentKind.of(key.opponentKind)
        )
        viewModelScope.launch { coordinator.state.collect(::onCoordinator) }
        viewModelScope.launch {
            coordinator.failure.collect { error ->
                if (error != null) _state.update { it.copy(error = BattleErrorText.of(error)) }
            }
        }
    }

    /** 그만두기 짧게 누름: 길게 눌러야 한다는 안내만(D-49). */
    fun onForfeitTapped() = showHint(FORFEIT_HINT)

    /** 그만두기 길게 누름: 확인을 받는다(FR-094). */
    fun onForfeitLongPressed() {
        val phase = _state.value.phase
        if (phase == BattlePhase.RUNNING || phase == BattlePhase.CLOSING || phase == BattlePhase.COUNTDOWN) {
            _state.update { it.copy(confirmingForfeit = true) }
        }
    }

    fun onForfeitDismissed() = _state.update { it.copy(confirmingForfeit = false) }

    fun onForfeitConfirmed() {
        _state.update { it.copy(confirmingForfeit = false) }
        coordinator.forfeit()
    }

    /** 시작하지 못하고 끝난 화면을 닫을 때. */
    fun onFailureClosed() = coordinator.acknowledgeFinished()

    private fun onCoordinator(s: BattleRunState) {
        val battleId = _state.value.battleId
        when (s) {
            is BattleRunState.Countdown -> if (s.battleId == battleId) {
                countdownTo = s.startAt
                startedAt = s.startAt
                _state.update { it.copy(phase = BattlePhase.COUNTDOWN) }
                tick()
            }

            is BattleRunState.Running -> if (s.battleId == battleId) {
                if (!sawRunning) {
                    sawRunning = true
                    if (startedAt == null || clock.instant().isBefore(startedAt)) startedAt = clock.instant()
                }
                // 조정자는 마지막 안내를 계속 들고 있으므로 바뀐 때만 새 안내로 본다.
                val changed = s.lastGuidance != null && s.lastGuidance != lastGuidanceSeen
                if (changed) lastGuidanceSeen = s.lastGuidance
                val overtook = if (changed && s.lastGuidance == BattleGuidance.Kind.OVERTOOK) 1 else 0
                _state.update {
                    it.copy(
                        phase = BattlePhase.RUNNING,
                        countdown = null,
                        gapMeters = s.gapMeters ?: it.gapMeters,
                        opponentDistance = s.opponentDistance ?: it.opponentDistance,
                        overtakes = it.overtakes + overtook
                    )
                }
                if (changed) showGuidance(s.lastGuidance)
                tick()
            }

            is BattleRunState.Closing -> if (s.battleId == battleId) {
                closesAt = s.endsAt
                _state.update { it.copy(phase = BattlePhase.CLOSING, arrivedName = s.arrivedName) }
                tick()
            }

            is BattleRunState.Finished -> if (s.battleId == battleId) {
                tickJob?.cancel()
                if (sawRunning) {
                    _state.update { it.copy(phase = BattlePhase.FINISHED, confirmingForfeit = false) }
                    _events.trySend(BattleRunningEvent.Finished(battleId))
                } else {
                    _state.update {
                        it.copy(
                            phase = BattlePhase.FAILED,
                            confirmingForfeit = false,
                            error = it.error ?: "대결을 시작하지 못했어요. 위치 권한과 연결을 확인해 주세요"
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    /** 1초마다 경과·카운트다운 남은 초를 갱신한다. 끝나면 멈춘다. */
    private fun tick() {
        if (tickJob?.isActive == true) {
            refreshClock()
            return
        }
        tickJob = viewModelScope.launch {
            while (isActive) {
                refreshClock()
                val phase = _state.value.phase
                if (phase == BattlePhase.FINISHED || phase == BattlePhase.FAILED) break
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
                elapsed = startedAt?.takeIf { s.phase == BattlePhase.RUNNING || s.phase == BattlePhase.CLOSING }
                    ?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) } ?: s.elapsed,
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

    private fun showHint(text: String) {
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
        const val FORFEIT_HINT = "길게 눌러 그만두기"
    }
}
