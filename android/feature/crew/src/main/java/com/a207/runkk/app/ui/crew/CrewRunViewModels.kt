package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.CrewRunState
import com.a207.runkk.app.domain.crew.Lobby
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

// ---------- 크루런 모집글 쓰기 ----------

data class CrewRunWriteUiState(
    val title: String = "",
    val body: String = "",
    val distance: Distance = Distance(5_000),
    /** 오늘부터 며칠 뒤(0~6). */
    val dayOffset: Int = 1,
    val time: LocalTime = LocalTime.of(DEFAULT_HOUR, 0),
    val today: LocalDate = LocalDate.MIN,
    val saving: Boolean = false,
    val error: UiText? = null
) {
    val valid: Boolean get() = title.isNotBlank()

    companion object {
        const val DEFAULT_HOUR = 7
        const val DAYS = 7
        val TIMES: List<LocalTime> = (5..23).flatMap { h -> listOf(LocalTime.of(h, 0), LocalTime.of(h, 30)) }
    }
}

sealed interface CrewRunWriteEvent {
    data object Posted : CrewRunWriteEvent
}

/**
 * 크루런 모집글 쓰기(FR-132): 제목·내용·거리(1·3·5·10km)·모집 일시. 장소 칸은 두지 않는다(비대면).
 * 모집 일시가 지났으면 서버가 거부한다.
 */
@HiltViewModel
class CrewRunWriteViewModel @Inject constructor(private val crews: CrewRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(CrewRunWriteUiState(today = LocalDate.now(clock)))
    val state: StateFlow<CrewRunWriteUiState> = _state.asStateFlow()

    private val _events = Channel<CrewRunWriteEvent>(Channel.BUFFERED)
    val events: Flow<CrewRunWriteEvent> = _events.receiveAsFlow()

    fun onTitle(text: String) = _state.update { it.copy(title = text.take(TITLE_MAX), error = null) }

    fun onBody(text: String) = _state.update { it.copy(body = text.take(BODY_MAX)) }

    fun onDistance(distance: Distance) {
        if (distance in CREW_RUN_DISTANCES) _state.update { it.copy(distance = distance) }
    }

    fun onDay(offset: Int) = _state.update { it.copy(dayOffset = offset.coerceIn(0, CrewRunWriteUiState.DAYS - 1)) }

    fun onTime(time: LocalTime) = _state.update { it.copy(time = time) }

    fun onSubmit(crewId: Long) {
        val s = _state.value
        if (!s.valid || s.saving) return
        val meetAt = s.today.plusDays(s.dayOffset.toLong()).atTime(s.time).atZone(clock.zone).toInstant()
        if (!meetAt.isAfter(clock.instant())) {
            _state.update { it.copy(error = uiText(R.string.crew_run_write_past_time)) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val form = CrewRunForm(s.title.trim(), s.body.trim().ifBlank { null }, s.distance, meetAt)
            when (val r = crews.writeCrewRun(crewId, form)) {
                is Outcome.Success -> {
                    _state.update { it.copy(saving = false) }
                    _events.trySend(CrewRunWriteEvent.Posted)
                }

                is Outcome.Failure -> _state.update { it.copy(saving = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    private companion object {
        const val TITLE_MAX = 30
        const val BODY_MAX = 200
    }
}

// ---------- 크루런 대기실·진행 ----------

/** 크루런 화면 단계: 대기실 → 카운트다운 → 진행 중 → 끝(결과로). */
enum class CrewRunPhase { ENTERING, LOBBY, COUNTDOWN, RUNNING, FINISHED, CANCELLED }

/** 중계 한 줄(FR-149). 위치는 없다. [me]는 내 줄(강조해 보인다). */
data class RelayRow(
    val name: String,
    val distance: Distance,
    val rank: Int?,
    val connection: String,
    val me: Boolean = false
)

data class CrewRunUiState(
    val postId: Long = 0,
    /** 크루런 거리(대기실 응답). 들어오기 전이면 null. */
    val distance: Distance? = null,
    val phase: CrewRunPhase = CrewRunPhase.ENTERING,
    val lobby: Lobby? = null,
    /** 내 준비 완료 여부(대기실 응답의 내 줄, me). */
    val ready: Boolean = false,
    /** 모집 일시까지 남은 시간. */
    val untilStart: Duration? = null,
    val countdown: Long? = null,
    val relay: List<RelayRow> = emptyList(),
    val cancelledReason: String? = null,
    val confirmingQuit: Boolean = false,
    val leaving: Boolean = false,
    val hint: UiText? = null,
    val busy: Boolean = false,
    val error: UiText? = null
)

sealed interface CrewRunEvent {
    data class Finished(val postId: Long) : CrewRunEvent

    data object Left : CrewRunEvent
}

/**
 * 크루런 대기실 → 동시 출발 → 진행 중 중계(FR-147~FR-149, FR-190, FR-192). enterLobby 성공 뒤 CrewRunCoordinator.onLobbyEntered로
 * 채널을 연다. 이후 시작·중계·종료는 조정자 상태를 보이고, 중간 그만두기는 길게 누름 + 확인 뒤 quit(D-49).
 */
@HiltViewModel
class CrewRunViewModel @Inject constructor(
    private val crews: CrewRepository,
    private val coordinator: CrewRunCoordinator,
    private val clock: Clock
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

    /**
     * 대기실 입장(또는 새로 읽기). 모집 일시 30분 전부터 열린다(FR-147). 처음 들어오면 대기실 응답의 크루런 거리로 조정자 채널을 연다.
     * 중계의 내 줄은 조정자가 CrewRunRelay.Entry.me로 가려 1km 순위 안내(FR-190)에 쓴다.
     */
    fun enter() {
        val s = _state.value
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = crews.enterLobby(s.postId)) {
                is Outcome.Success -> {
                    val first = _state.value.lobby == null
                    _state.update {
                        it.copy(
                            busy = false,
                            lobby = r.value,
                            distance = r.value.distance,
                            ready = r.value.myReady() ?: it.ready,
                            phase = if (it.phase == CrewRunPhase.ENTERING) CrewRunPhase.LOBBY else it.phase
                        )
                    }
                    if (first) coordinator.onLobbyEntered(s.postId, r.value.distance)
                    tick()
                }

                is Outcome.Failure -> _state.update { it.copy(busy = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    /** 준비 완료 ↔ 해제(FR-147). */
    fun onReadyToggle() {
        val s = _state.value
        if (s.busy || s.phase != CrewRunPhase.LOBBY) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = crews.toggleReady(s.postId)) {
                is Outcome.Success -> _state.update {
                    it.copy(busy = false, lobby = r.value, ready = r.value.myReady() ?: !it.ready)
                }

                is Outcome.Failure -> _state.update { it.copy(busy = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    /**
     * 대기실 나가기. 조정자가 서버에 나가기를 알리고 중계 채널을 닫는다(CrewRunCoordinator.leaveLobby). 성공하면 이전 화면으로,
     * 실패하면 사유를 보인다.
     */
    fun onLeaveLobby() {
        val s = _state.value
        if (s.leaving) return
        _state.update { it.copy(leaving = true) }
        viewModelScope.launch {
            when (val r = coordinator.leaveLobby(s.postId)) {
                is Outcome.Success -> _events.trySend(CrewRunEvent.Left)
                is Outcome.Failure -> _state.update { it.copy(leaving = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onQuitTapped() = showHint(uiText(R.string.crew_run_quit_hint))

    fun onQuitLongPressed() {
        if (_state.value.phase == CrewRunPhase.RUNNING || _state.value.phase == CrewRunPhase.COUNTDOWN) {
            _state.update { it.copy(confirmingQuit = true) }
        }
    }

    fun onQuitDismissed() = _state.update { it.copy(confirmingQuit = false) }

    /** 확인 뒤 중간 그만두기(FR-192). 그때까지 달린 기록은 저장된다. */
    fun onQuitConfirmed() {
        _state.update { it.copy(confirmingQuit = false) }
        coordinator.quit()
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
                _state.update { st ->
                    st.copy(
                        phase = CrewRunPhase.RUNNING,
                        countdown = null,
                        relay = s.entries
                            .map { e -> RelayRow(e.name, e.distance, e.rank, e.connection.name, e.me) }
                            .sortedWith(
                                compareBy<RelayRow> {
                                    it.rank ?: Int.MAX_VALUE
                                }.thenByDescending { it.distance }
                            )
                    )
                }
            }

            is CrewRunState.Finished -> if (s.postId == postId) {
                tickJob?.cancel()
                if (s.cancelledReason != null) {
                    _state.update { it.copy(phase = CrewRunPhase.CANCELLED, cancelledReason = s.cancelledReason) }
                } else {
                    _state.update { it.copy(phase = CrewRunPhase.FINISHED, confirmingQuit = false) }
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
        _state.update { s ->
            s.copy(
                untilStart = s.lobby?.meetAt?.let { Duration.between(now, it).coerceAtLeast(Duration.ZERO) },
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
private fun Lobby.myReady(): Boolean? = participants.firstOrNull { it.me }?.ready

// ---------- 크루런 결과 ----------

data class CrewRunResultUiState(
    val loading: Boolean = true,
    val result: CrewRunResult? = null,
    val error: UiText? = null
) {
    val finishedCount: Int get() = result?.finishers?.count { it.rank != null } ?: 0
}

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

    fun load(postId: Long, force: Boolean = false) {
        if (!force && loaded == postId) return
        loaded = postId
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            when (val r = crews.crewRunResult(postId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(loading = false, result = r.value) }
                    coordinator.acknowledgeFinished()
                }

                is Outcome.Failure -> {
                    loaded = null
                    _state.update { it.copy(loading = false, error = CrewErrorText.of(r.error)) }
                }
            }
        }
    }
}
