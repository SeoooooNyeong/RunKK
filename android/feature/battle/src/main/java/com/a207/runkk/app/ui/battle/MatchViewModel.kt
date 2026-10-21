package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.MatchFound
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 랜덤 매칭 단계: 거리 고르기 → 대기 → 성사(자동 출발까지). */
enum class MatchPhase { CHOOSING, REQUESTING, WAITING, CANCELLING, MATCHED }

data class MatchUiState(
    val phase: MatchPhase = MatchPhase.CHOOSING,
    val distance: Distance = DEFAULT_DISTANCE,
    /** 대기 시작 뒤 경과 초(시안 "0:07"). */
    val waitingSeconds: Long = 0,
    val match: MatchFound? = null,
    /** 자동 출발까지 남은 초. 시작 예약(BattleStart)을 받기 전이면 null. */
    val startsInSeconds: Long? = null,
    val error: String? = null
) {
    companion object {
        /** 랜덤 대결 거리(FR-080). */
        val DISTANCES: List<Distance> = listOf(Distance(1_000), Distance(3_000), Distance(5_000), Distance(10_000))

        /** 기본 대결 거리(FR-109 기본값). 저장된 기본 거리는 ui.battle이 읽을 포트가 없어 쓰지 않는다. */
        val DEFAULT_DISTANCE: Distance = Distance(5_000)
    }
}

sealed interface MatchEvent {
    /** 측정 시작: 대결 진행 화면으로(이 화면을 대신한다). */
    data class Started(val key: Routes.BattleRunning) : MatchEvent

    /** 대기 취소: 이전 화면으로. */
    data object Exit : MatchEvent
}

/**
 * 랜덤 매칭 대기·성사(FR-080~FR-087, FR-095). requestMatch로 대기열에 들어가고 실시간 채널에서 MatchFound를 받으면
 * BattleRunCoordinator.onMatched로 넘긴다(채널은 LiveChannelProvider가 같은 연결을 돌려준다, D-57). 이후 시작 예약·측정 시작은
 * 조정자 상태(Matched → Countdown → Running)를 따라 보인다. 15초 뒤 고스트 전환은 서버가 하고 화면은 같은 흐름이다(Q-02).
 */
@HiltViewModel
class MatchViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val channels: LiveChannelProvider,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(MatchUiState())
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private val _events = Channel<MatchEvent>(Channel.BUFFERED)
    val events: Flow<MatchEvent> = _events.receiveAsFlow()

    private var channel: LiveChannel? = null
    private var waitJob: Job? = null
    private var tickJob: Job? = null
    private var followJob: Job? = null
    private var handedOver = false

    fun onDistanceSelected(distance: Distance) {
        if (_state.value.phase != MatchPhase.CHOOSING || distance !in MatchUiState.DISTANCES) return
        _state.update { it.copy(distance = distance) }
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    /** 매칭 요청(FR-080). 거부(언랭·이미 대기 중·동의 없음)는 문구로 보이고 거리 고르기로 남는다. */
    fun onStartMatching() {
        if (_state.value.phase != MatchPhase.CHOOSING) return
        _state.update { it.copy(phase = MatchPhase.REQUESTING, error = null) }
        viewModelScope.launch {
            // 조정자가 applicationScope에서 보낸다: 요청 중 화면이 사라져도 이탈 취소가 요청 뒤에 나간다.
            when (val accepted = coordinator.requestMatch(_state.value.distance)) {
                is Outcome.Success -> startWaiting()

                is Outcome.Failure -> _state.update {
                    it.copy(phase = MatchPhase.CHOOSING, error = BattleErrorText.of(accepted.error))
                }
            }
        }
    }

    /** 대기 취소(FR-085). 이미 배정됐으면 서버가 거부하고 성사 화면을 기다린다. */
    fun onCancel() {
        when (_state.value.phase) {
            MatchPhase.CHOOSING -> _events.trySend(MatchEvent.Exit)

            MatchPhase.WAITING -> {
                _state.update { it.copy(phase = MatchPhase.CANCELLING) }
                viewModelScope.launch {
                    when (val cancelled = battles.cancel()) {
                        // 기다리는 사이 성사됐으면(MATCHED) 취소 응답을 무시한다.
                        is Outcome.Success -> if (_state.value.phase == MatchPhase.CANCELLING) {
                            stopWaiting()
                            _events.trySend(MatchEvent.Exit)
                        }

                        // 이미 배정됨(ALREADY_MATCHED)이면 곧 MatchFound가 온다. 그 밖의 실패도 대기를 이어 간다.
                        is Outcome.Failure -> _state.update {
                            it.copy(
                                phase = if (it.phase == MatchPhase.CANCELLING) MatchPhase.WAITING else it.phase,
                                error = BattleErrorText.of(cancelled.error)
                            )
                        }
                    }
                }
            }

            // 요청 중·성사 뒤에는 뒤로 가지 않는다(자동 출발, FR-087).
            else -> Unit
        }
    }

    private fun startWaiting() {
        waitJob?.cancel()
        tickJob?.cancel()
        _state.update { it.copy(phase = MatchPhase.WAITING, waitingSeconds = 0) }
        val opened = channels.open().also { channel = it }
        waitJob = viewModelScope.launch {
            val match = try {
                coroutineScope {
                    launch { if (opened.connect() is Outcome.Failure) throw ChannelConnectFailed() }
                    opened.messages.filterIsInstance<MatchFound>().first().also { coroutineContext.cancelChildren() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                // 연결 실패·채널 종료: 대기열에서 빼고 거리 고르기로 돌아간다(앱을 죽이지 않는다).
                failWaiting()
                return@launch
            }
            onMatched(match)
        }
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                _state.update {
                    if (it.phase ==
                        MatchPhase.WAITING
                    ) {
                        it.copy(waitingSeconds = it.waitingSeconds + 1)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private suspend fun failWaiting() {
        tickJob?.cancel()
        channel?.close()
        channel = null
        battles.cancel()
        _state.update { MatchUiState(distance = it.distance, error = "상대를 찾는 연결이 끊겼어요. 다시 시도해 주세요") }
    }

    /** 성사: 채널은 이제 조정자가 갖는다(대기용 연결 코루틴은 닫는다). */
    private fun onMatched(match: MatchFound) {
        tickJob?.cancel()
        handedOver = true
        channel = null
        _state.update { it.copy(phase = MatchPhase.MATCHED, match = match, distance = match.distance) }
        coordinator.onMatched(match)
        follow(match)
    }

    /** 조정자 상태를 따라 자동 출발까지 남은 초를 보이고, 측정이 시작되면 대결 진행 화면으로 간다. */
    private fun follow(match: MatchFound) {
        followJob?.cancel()
        followJob = viewModelScope.launch {
            coordinator.state.collect { s ->
                when (s) {
                    is BattleRunState.Countdown -> if (s.battleId == match.battleId) countdownTo(s)

                    is BattleRunState.Running -> if (s.battleId == match.battleId) started(match)

                    is BattleRunState.Finished -> if (s.battleId == match.battleId && !navigated) {
                        // 시작 전에 끝남(연결 실패·시작 거부). 결과가 없으니 안내하고 거리 고르기로 돌린다.
                        val reason = coordinator.failure.value
                        countdownJob?.cancel()
                        waitJob?.cancel()
                        handedOver = false
                        _state.update {
                            MatchUiState(
                                distance = it.distance,
                                error = reason?.let(BattleErrorText::of) ?: "대결을 시작하지 못했어요. 다시 시도해 주세요"
                            )
                        }
                        coordinator.acknowledgeFinished()
                        followJob?.cancel()
                    }

                    else -> Unit
                }
            }
        }
    }

    private var countdownJob: Job? = null

    private var startAt: java.time.Instant? = null

    private fun countdownTo(s: BattleRunState.Countdown) {
        startAt = s.startAt
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val left = Duration.between(clock.instant(), s.startAt).seconds.coerceAtLeast(0)
                _state.update { it.copy(startsInSeconds = left) }
                if (left == 0L) break
                delay(TICK_MILLIS)
            }
        }
    }

    private var navigated = false

    private fun started(match: MatchFound) {
        if (navigated) return
        navigated = true
        countdownJob?.cancel()
        _events.trySend(
            MatchEvent.Started(
                Routes.BattleRunning(
                    match.battleId,
                    match.opponentName,
                    match.distance.meters,
                    OpponentKind.RANDOM.name,
                    startAtEpochMillis = startAt?.toEpochMilli()
                )
            )
        )
    }

    private fun stopWaiting() {
        waitJob?.cancel()
        tickJob?.cancel()
        channel?.close()
        channel = null
        _state.update { MatchUiState(distance = it.distance) }
    }

    override fun onCleared() {
        // 성사 전에 화면을 떠나면(시스템 제스처·탭 이동으로 ViewModel이 지워지면) 대기 채널을 닫고, 서버 대기열에서도 뺀다.
        // viewModelScope는 이미 취소됐으므로 조정자가 applicationScope에서 보낸다(FR-085). 성사 뒤 채널·대결은 조정자가 갖는다.
        if (handedOver) return
        channel?.close()
        channel = null
        if (_state.value.phase in QUEUED) coordinator.abandonMatching()
    }

    private companion object {
        const val TICK_MILLIS = 1_000L

        /** 서버 대기열에 들어갔거나 들어가는 중인 단계(요청 중이면 응답 뒤 대기열에 남을 수 있다). */
        val QUEUED = setOf(MatchPhase.REQUESTING, MatchPhase.WAITING, MatchPhase.CANCELLING)
    }
}

/** 채널 연결이 Outcome.Failure로 끝났다. 대기를 접고 거리 고르기로 돌아간다. */
private class ChannelConnectFailed : Exception()
