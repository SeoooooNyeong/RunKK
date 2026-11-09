package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
import com.a207.runkk.app.domain.crew.CrewRunState
import com.a207.runkk.app.domain.crew.Lobby
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

/**
 * 크루런 대기실 → 동시 출발 → 진행(합산 방식, S15P31A207-236, 피그마 F-14~F-15d). enterLobby 성공 뒤
 * CrewRunCoordinator.onLobbyEntered로 채널을 연다. 이후 시작·중계·내 측정값·종료는 조정자 상태를 보이고, 일시정지·재개는 조정자에
 * 넘기며, 내 크루런 끝내기는 길게 누름 + 확인 뒤 quit(D-49, F-15d).
 * 출발까지 남은 시간(모집 일시는 서버 시각)은 서버 시각 추정(ServerClock)으로 세고, 출발 카운트다운은 조정자가 측정을 여는
 * 기기 시계(clock)로 센다.
 */
@HiltViewModel
class CrewRunViewModel @Inject constructor(
    private val crews: CrewRepository,
    private val coordinator: CrewRunCoordinator,
    private val clock: Clock,
    private val serverClock: ServerClock
) : ViewModel() {
    private val _state = MutableStateFlow(CrewRunUiState())
    val state: StateFlow<CrewRunUiState> = _state.asStateFlow()

    private val _events = Channel<CrewRunEvent>(Channel.BUFFERED)
    val events: Flow<CrewRunEvent> = _events.receiveAsFlow()

    private var bound: Long? = null
    private var tickJob: Job? = null
    private var hintJob: Job? = null
    private var startAt: Instant? = null

    fun bind(key: Routes.CrewRunLobby) {
        if (bound == key.postId) return
        bound = key.postId
        _state.value = CrewRunUiState(postId = key.postId)
        enter()
        // 들어올 때 이미 이 크루런의 Finished가 남아 있으면(지난 회의 잔재) 무시한다.
        val stale = (coordinator.state.value as? CrewRunState.Finished)?.takeIf { it.postId == key.postId }
        viewModelScope.launch { coordinator.state.collect { if (it !== stale) onCoordinator(it) } }
        viewModelScope.launch {
            coordinator.failure.collect { e -> if (e != null) _state.update { it.copy(error = CrewErrorText.of(e)) } }
        }
    }

    /** 대기실 입장(또는 새로 읽기). 모집 일시 30분 전부터 열린다(FR-147). 처음 들어오면 대기실 응답의 크루런 거리로 조정자 채널을 연다. */
    fun enter() {
        val s = _state.value
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val r = crews.enterLobby(s.postId)) {
                is Outcome.Success -> {
                    val first = _state.value.lobby == null
                    _state.update {
                        it.copy(
                            isBusy = false,
                            lobby = r.value,
                            distance = r.value.distance,
                            isReady = r.value.myReady() ?: it.isReady,
                            phase = if (it.phase == CrewRunPhase.ENTERING) CrewRunPhase.LOBBY else it.phase
                        )
                    }
                    if (first) coordinator.onLobbyEntered(s.postId, r.value.distance)
                    tick()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isBusy = false, error = CrewErrorText.of(r.error), canRetry = r.error !is AppError.Rejected)
                }
            }
        }
    }

    /**
     * 뒤로 가기(상단 뒤로 단추·시스템 뒤로 가기·오류의 돌아가기). 대기실에 들어가지 못했으면(ENTERING) 나가기 요청 없이 닫고,
     * 대기실이면 나가기, 출발 카운트다운·진행 중이면 그만두기 안내만, 취소됐으면 조정자에 알린 뒤 닫는다.
     */
    fun onBack() {
        when (_state.value.phase) {
            CrewRunPhase.ENTERING -> _events.trySend(CrewRunEvent.Left)

            CrewRunPhase.LOBBY, CrewRunPhase.FINISHED -> onLeaveLobby()

            CrewRunPhase.COUNTDOWN, CrewRunPhase.RUNNING -> onQuitTapped()

            CrewRunPhase.CANCELLED -> {
                onCancelledClosed()
                _events.trySend(CrewRunEvent.Left)
            }
        }
    }

    /** 준비 완료 ↔ 해제(FR-147). */
    fun onReadyToggle() {
        val s = _state.value
        if (s.isBusy || s.phase != CrewRunPhase.LOBBY) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (val r = crews.toggleReady(s.postId)) {
                is Outcome.Success -> _state.update {
                    it.copy(isBusy = false, lobby = r.value, isReady = r.value.myReady() ?: !it.isReady)
                }

                is Outcome.Failure -> _state.update { it.copy(isBusy = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    /**
     * 대기실 나가기. 조정자가 서버에 나가기를 알리고 중계 채널을 닫는다(CrewRunCoordinator.leaveLobby). 성공하면 이전 화면으로,
     * 실패하면 사유를 보인다.
     */
    fun onLeaveLobby() {
        val s = _state.value
        if (s.isLeaving) return
        _state.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            when (val r = coordinator.leaveLobby(s.postId)) {
                is Outcome.Success -> _events.trySend(CrewRunEvent.Left)
                is Outcome.Failure -> _state.update { it.copy(isLeaving = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onQuitTapped() = showHint(uiText(R.string.crew_run_quit_hint))

    fun onQuitLongPressed() {
        if (_state.value.phase == CrewRunPhase.RUNNING || _state.value.phase == CrewRunPhase.COUNTDOWN) {
            _state.update { it.copy(isConfirmingQuit = true) }
        }
    }

    fun onQuitDismissed() = _state.update { it.copy(isConfirmingQuit = false) }

    /** 확인 뒤 내 크루런 끝내기(F-15d). 그때까지 달린 기록은 저장된다. */
    fun onQuitConfirmed() {
        _state.update { it.copy(isConfirmingQuit = false) }
        coordinator.quit()
    }

    /** 일시정지(F-15c). 서버 보고는 조정자가 한다. */
    fun onPause() {
        if (_state.value.phase == CrewRunPhase.RUNNING) coordinator.pause()
    }

    /** 재개(F-15c). */
    fun onResume() {
        if (_state.value.phase == CrewRunPhase.RUNNING) coordinator.resume()
    }

    /** 취소된 크루런 화면을 닫을 때. */
    fun onCancelledClosed() = coordinator.acknowledgeFinished()

    private fun onCoordinator(s: CrewRunState) {
        val postId = _state.value.postId
        when (s) {
            is CrewRunState.Countdown -> if (s.postId == postId) {
                startAt = s.startAt
                _state.update { it.copy(phase = CrewRunPhase.COUNTDOWN) }
                tick()
            }

            is CrewRunState.Running -> if (s.postId == postId) {
                // 줄 순서는 서버가 준 대기실 입장 순 그대로다(순위가 없다).
                _state.update { st ->
                    st.copy(
                        phase = CrewRunPhase.RUNNING,
                        countdown = null,
                        relay = s.entries.map { e ->
                            RelayRow(
                                e.name,
                                e.distance,
                                MateState.of(e.state.name),
                                e.isMe,
                                st.lobby.genderOf(e.memberId)
                            )
                        },
                        totalDistance = s.totalDistance,
                        mine = s.mine
                    )
                }
            }

            is CrewRunState.Finished -> if (s.postId == postId) {
                tickJob?.cancel()
                if (s.cancelledReason != null) {
                    // 취소 화면(F-14c)은 대기실 틀이다: 마지막 대기실(참가자·준비 상태)을 그대로 두고 남은 시간은 0:00이다.
                    _state.update {
                        it.copy(
                            phase = CrewRunPhase.CANCELLED,
                            cancelledReason = s.cancelledReason,
                            untilStart = Duration.ZERO,
                            countdown = null
                        )
                    }
                } else {
                    _state.update { it.copy(phase = CrewRunPhase.FINISHED, isConfirmingQuit = false) }
                    _events.trySend(CrewRunEvent.Finished(postId))
                }
            }

            else -> Unit
        }
    }

    /** 1초마다 모집 일시까지 남은 시간·카운트다운을 갱신한다. 진행 중·끝이면 멈춘다. */
    private fun tick() {
        if (tickJob?.isActive == true) {
            refreshClock()
            return
        }
        tickJob = viewModelScope.launch {
            while (isActive) {
                refreshClock()
                val phase = _state.value.phase
                if (phase != CrewRunPhase.LOBBY && phase != CrewRunPhase.COUNTDOWN) break
                delay(TICK_MILLIS)
            }
        }
    }

    private fun refreshClock() {
        val now = clock.instant()
        val serverNow = serverClock.now()
        _state.update { s ->
            s.copy(
                untilStart = s.lobby?.meetAt?.let { Duration.between(serverNow, it).coerceAtLeast(Duration.ZERO) },
                countdown = startAt?.takeIf { s.phase == CrewRunPhase.COUNTDOWN }
                    ?.let { Duration.between(now, it).seconds.coerceAtLeast(0) }
            )
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
        const val HINT_MILLIS = 2_500L
    }
}

/** 대기실 응답의 내 줄(me) 준비 여부. 내 줄이 없으면 null. */
private fun Lobby.myReady(): Boolean? = participants.firstOrNull { it.isMe }?.isReady

/** 중계 줄 회원의 표시 성별(대기실 응답의 같은 memberId 줄, 크루원 아바타). 중계에는 아바타 값을 싣지 않는다(계약 crewrun-sum v1.2). */
private fun Lobby?.genderOf(memberId: Long?): Gender? =
    memberId?.let { id -> this?.participants?.firstOrNull { it.memberId == id }?.shownGender }
