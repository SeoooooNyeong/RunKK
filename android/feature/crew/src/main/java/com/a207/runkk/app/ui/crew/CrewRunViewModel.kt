package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunViewMode
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.account.myAvatar
import com.a207.runkk.app.domain.crew.CrewChange
import com.a207.runkk.app.domain.crew.CrewReadSession
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
import com.a207.runkk.app.domain.crew.CrewRunSignals
import com.a207.runkk.app.domain.crew.CrewRunStanding
import com.a207.runkk.app.domain.crew.CrewRunState
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.LobbyPhase
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 크루런 대기실 → 동시 출발 → 진행(합산 방식, S15P31A207-236, 피그마 F-14~F-15d). 대기실을 읽은 뒤
 * CrewRunCoordinator.onLobbyEntered로 채널을 연다. 이후 시작·중계·내 측정값·종료는 조정자 상태를 보이고, 일시정지·재개는 조정자에
 * 넘기며, 내 크루런 끝내기는 길게 누름 + 확인 뒤 quit(D-49, F-15d).
 *
 * 실시간 반영(설계 05-32): 들어오면 GET 대기실을 먼저 읽고 아직 입장하지 않았을 때만 enter를 보낸다(다시 enter하면 READY가 풀린다,
 * [임시:D-성-879]). 화면이 보이는 동안 이 크루런의 변경 신호(CrewRunChanged LOBBY)를 받으면 대기실만 다시 읽고(CrewReadSession으로
 * 합친다), 화면에 다시 들어오거나 당겨서 새로고침하면 읽는다. 시간 주기 읽기는 없다(U-32). 준비는 멱등 PUT, 모집자는 「지금
 * 시작」(canStartNow), 출발 뒤 진행 참가자가 아니면 「함께 뛰기」·「나가기」 시트(JoinChoice)다. 크루런 탭 「중간 참여하기」로 왔으면
 * 묻지 않고 바로 참여한다.
 * 출발까지 남은 시간(모집 일시는 서버 시각)은 서버 시각 추정(ServerClock)으로 세고, 출발 카운트다운은 조정자가 측정을 여는
 * 기기 시계(clock)로 센다.
 */
@HiltViewModel
class CrewRunViewModel internal constructor(
    private val crews: CrewRepository,
    private val coordinator: CrewRunCoordinator,
    private val clock: Clock,
    private val serverClock: ServerClock,
    /** 보기 선호 기기 사본(대결과 같은 값, [임시:D-성-264]). */
    private val readViewMode: suspend () -> RunViewMode = { RunViewMode.TRACK },
    private val saveViewMode: suspend (RunViewMode) -> Unit = {},
    /** 내 아바타(홈과 같은 출처, AccountRepository.myAvatar). 트랙 배경과 내 얼굴에 쓴다. */
    private val myAvatar: suspend () -> MyAvatar? = { null },
    private val lastAvatar: () -> MyAvatar? = { null },
    /** 크루런 변경 신호(CrewRunSignals.changes). 수집하는 동안만 연결 임대를 빌린다. */
    private val changes: () -> Flow<CrewChange> = { emptyFlow() }
) : ViewModel() {
    @Inject
    constructor(
        crews: CrewRepository,
        coordinator: CrewRunCoordinator,
        clock: Clock,
        serverClock: ServerClock,
        account: AccountRepository,
        signals: CrewRunSignals
    ) : this(
        crews,
        coordinator,
        clock,
        serverClock,
        account::runViewMode,
        { account.saveRunViewMode(it) },
        { account.myAvatar() },
        account::lastMyAvatar,
        signals::changes
    )

    private val _state = MutableStateFlow(CrewRunUiState())
    val state: StateFlow<CrewRunUiState> = _state.asStateFlow()

    private val _events = Channel<CrewRunEvent>(Channel.BUFFERED)
    val events: Flow<CrewRunEvent> = _events.receiveAsFlow()

    private var bound: Long? = null
    private var tickJob: Job? = null
    private var hintJob: Job? = null
    private var startAt: Instant? = null

    /** 대기실 응답 없이 다시 들어왔을 때 결과 응답에서 읽은 크루원 표시 성별(memberId → 성별). */
    private var rejoinGenders: Map<Long, Gender> = emptyMap()
    private var genderJob: Job? = null

    /** 이미 받으러 간 대기실 얼굴 키(키마다 한 번). */
    private val requestedFaces = mutableSetOf<String>()

    /** 「중간 참여하기」로 들어왔다(달리는 크루런이면 바로 참여, 한 번만). */
    private var joinOnEntry = false

    /** 대기실 다시 읽기(신호·당기기·재진입을 합친다, [임시:D-성-880]). */
    private val lobbyReads = CrewReadSession(viewModelScope) { refreshLobby() }
    private var signalJob: Job? = null

    /** 화면이 가려졌다가 돌아오면 다시 읽는다(재진입 읽기, [임시:FR-성-372]). */
    private var wasHidden = false

    fun bind(key: Routes.CrewRunLobby) {
        if (bound == key.postId) return
        bound = key.postId
        joinOnEntry = key.join
        _state.value = CrewRunUiState(postId = key.postId, myAvatar = lastAvatar())
        viewModelScope.launch {
            val saved = readViewMode()
            _state.update { it.copy(viewMode = saved) }
        }
        viewModelScope.launch { myAvatar()?.let { mine -> _state.update { it.copy(myAvatar = mine) } } }
        enter()
        // 들어올 때 이미 이 크루런의 Finished가 남아 있으면(지난 회의 잔재) 무시한다.
        val stale = (coordinator.state.value as? CrewRunState.Finished)?.takeIf { it.postId == key.postId }
        viewModelScope.launch { coordinator.state.collect { if (it !== stale) onCoordinator(it) } }
        viewModelScope.launch {
            coordinator.failure.collect { e ->
                if (e != null) _state.update { it.copy(error = CrewErrorText.of(e), isJoining = false) }
            }
        }
    }

    /**
     * 대기실 들어가기(또는 오류 뒤 다시 시도). 모집 일시 30분 전부터 열린다(FR-147). GET으로 먼저 읽고 아직 입장하지 않은 대기실일
     * 때만 enter를 보낸다(설계 05-32 [임시:D-성-879]). GET이 없는 옛 서버(5xx·404·405)면 예전처럼 enter로 들어간다. 처음 들어오면 대기실
     * 응답의 크루런 거리로 조정자 채널을 연다.
     */
    fun enter() {
        val s = _state.value
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val read = crews.lobby(s.postId)
            val entered = when {
                read is Outcome.Failure && read.error.isMissingApi() -> crews.enterLobby(s.postId)

                read is Outcome.Success && read.value.isEnterNeeded && read.value.isEnterable() -> crews.enterLobby(
                    s.postId
                )

                else -> read
            }
            when (entered) {
                is Outcome.Success -> applyLobby(entered.value)

                is Outcome.Failure -> _state.update {
                    it.copy(
                        isBusy = false,
                        error = CrewErrorText.of(entered.error),
                        canRetry = entered.error !is AppError.Rejected
                    )
                }
            }
        }
    }

    /**
     * 대기실 응답 하나(GET·enter·PUT·start)를 화면과 조정자에 반영한다. 처음이면 조정자 채널을 연다. 끝난 크루런이면 결과로,
     * 취소면 취소 화면으로 간다. 「중간 참여하기」로 왔고 달리는 크루런에 참여할 수 있으면 바로 참여한다.
     */
    private fun applyLobby(lobby: Lobby) {
        val first = _state.value.lobby == null
        // 끝났거나 취소된 크루런은 조정자 회·실시간 임대를 열지 않고, 이미 열었으면 닫는다(리뷰 결함 1).
        val isOver = lobby.phase == LobbyPhase.FINISHED || lobby.phase == LobbyPhase.CANCELLED
        _state.update {
            it.copy(
                isBusy = false,
                isRefreshing = false,
                lobby = lobby,
                looks = it.looks + lobby.looks(),
                distance = lobby.distance,
                isReady = lobby.myReady() ?: it.isReady,
                phase = if (it.phase == CrewRunPhase.ENTERING) CrewRunPhase.LOBBY else it.phase
            )
        }
        if (first && !isOver) coordinator.onLobbyEntered(lobby.postId, lobby.distance)
        if (isOver && !first) viewModelScope.launch { coordinator.dismiss(lobby.postId) }
        when (lobby.phase) {
            LobbyPhase.FINISHED -> if (first) _events.trySend(CrewRunEvent.Finished(lobby.postId))

            LobbyPhase.CANCELLED -> _state.update {
                it.copy(phase = CrewRunPhase.CANCELLED, untilStart = Duration.ZERO, countdown = null)
            }

            else -> Unit
        }
        if (!isOver) coordinator.onLobby(lobby)
        if (joinOnEntry && lobby.phase == LobbyPhase.RUNNING && lobby.canJoin) {
            joinOnEntry = false
            onJoin()
        }
        readLobbyFaces(lobby)
        tick()
    }

    /** 대기실만 다시 읽는다(신호·당기기·재진입). 실패하면 지금 값을 두고 안내한다. 진행 중이면 중계가 보이므로 읽지 않는다. */
    private suspend fun refreshLobby() {
        val s = _state.value
        if (s.lobby == null || s.phase == CrewRunPhase.RUNNING || s.phase == CrewRunPhase.FINISHED) {
            _state.update { it.copy(isRefreshing = false) }
            return
        }
        when (val r = crews.lobby(s.postId)) {
            is Outcome.Success -> applyLobby(r.value)
            is Outcome.Failure -> _state.update { it.copy(isRefreshing = false, error = CrewErrorText.of(r.error)) }
        }
    }

    /**
     * 화면이 보인다(LifecycleStartEffect): 이 크루런의 변경 신호를 받기 시작하고, 가려졌다 돌아왔으면 대기실을 한 번 읽는다.
     */
    fun onStart() {
        if (signalJob?.isActive != true) {
            signalJob = viewModelScope.launch {
                changes().collect { change ->
                    if (change.postId == _state.value.postId && change.resource == CrewChange.Resource.LOBBY) {
                        lobbyReads.refresh()
                    }
                }
            }
        }
        if (wasHidden) {
            wasHidden = false
            lobbyReads.refresh()
        }
    }

    /** 화면이 가려졌다: 신호 임대를 반납하고 읽기를 멈춘다. */
    fun onStop() {
        signalJob?.cancel()
        signalJob = null
        lobbyReads.cancel()
        wasHidden = true
    }

    /** 당겨서 새로고침(대기실). 읽는 중이면 끝난 뒤 한 번 더 읽는다. */
    fun onPullRefresh() {
        if (_state.value.lobby == null) return
        _state.update { it.copy(isRefreshing = true) }
        lobbyReads.refresh()
    }

    /**
     * 뒤로 가기(상단 뒤로 단추·시스템 뒤로 가기·오류의 돌아가기). 대기실에 들어가지 못했으면(ENTERING) 나가기 요청 없이 닫고,
     * 대기실이면 나가기, 출발 카운트다운·진행 중이면 그만두기 안내만, 취소됐으면 조정자에 알린 뒤 닫는다.
     */
    fun onBack() {
        when (_state.value.phase) {
            CrewRunPhase.ENTERING -> _events.trySend(CrewRunEvent.Left)

            CrewRunPhase.LOBBY, CrewRunPhase.FINISHED -> onLeaveLobby()

            CrewRunPhase.RUNNING -> onQuitTapped()

            // 카운트다운 중 뒤로 가기는 대기실 나가기다(후보에서만 빠진다, [임시:D-성-882]). 측정을 시작했으면 조정자가 거부한다.
            CrewRunPhase.COUNTDOWN -> onLeaveLobby()

            CrewRunPhase.JOIN_CHOICE -> onJoinDismissed()

            CrewRunPhase.CANCELLED -> {
                onCancelledClosed()
                _events.trySend(CrewRunEvent.Left)
            }
        }
    }

    /**
     * 준비 완료 ↔ 해제(FR-147). 바랄 값을 멱등 PUT으로 보낸다(설계 05-32, 같은 값을 다시 보내도 바뀌지 않는다). 카운트다운 중 해제는
     * 출발 후보에서만 빠진다(「준비 취소하고 기다리기」). PUT이 없는 옛 서버면 예전 토글을 보낸다.
     */
    fun onReadyToggle() {
        val s = _state.value
        if (s.isBusy || (s.phase != CrewRunPhase.LOBBY && s.phase != CrewRunPhase.COUNTDOWN)) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val wanted = !s.isReady
            val sent = crews.setReady(s.postId, wanted).let { r ->
                if (r is Outcome.Failure && r.error.isMissingApi()) crews.toggleReady(s.postId) else r
            }
            when (sent) {
                is Outcome.Success -> {
                    applyLobby(sent.value)
                    _state.update { it.copy(isReady = sent.value.myReady() ?: wanted) }
                }

                is Outcome.Failure -> _state.update { it.copy(isBusy = false, error = CrewErrorText.of(sent.error)) }
            }
        }
    }

    /** 모집자 「지금 시작」(설계 05-32 [임시:D-성-881]): 준비한 사람끼리 3초 뒤 출발한다. 누를 수 있는지는 서버 canStartNow를 따른다. */
    fun onStartNow() {
        val s = _state.value
        if (s.isStarting || s.lobby?.canStartNow != true || s.phase != CrewRunPhase.LOBBY) return
        _state.update { it.copy(isStarting = true, error = null) }
        viewModelScope.launch {
            when (val r = crews.startNow(s.postId)) {
                is Outcome.Success -> {
                    applyLobby(r.value)
                    _state.update { it.copy(isStarting = false) }
                }

                is Outcome.Failure -> _state.update { it.copy(isStarting = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    /** 「함께 뛰기」·「중간 참여하기」: 조정자가 참여(POST join)하고 승인 시각부터 잰다. 실패는 조정자 failure로 안내한다. */
    fun onJoin() {
        if (_state.value.isJoining) return
        _state.update { it.copy(isJoining = true, error = null) }
        coordinator.join()
    }

    /** 출발 시트의 「나가기」: 서버에 알리지 않고 이 회를 닫고 이전 화면으로 간다(설계 05-32 [임시:D-성-883]). */
    fun onJoinDismissed() {
        val postId = _state.value.postId
        viewModelScope.launch {
            coordinator.dismiss(postId)
            _events.trySend(CrewRunEvent.Left)
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

    /**
     * 「트랙 | 지도」 전환([임시:FR-성-28]): 고른 보기를 바로 보이고 기기에 저장해 다음 크루런과 대결에서도 쓴다. 지도 오류로 트랙만 보일
     * 때는 화면이 이 함수를 부르지 않아 저장값이 그대로다.
     */
    fun onViewModeChange(mode: RunViewMode) {
        if (_state.value.viewMode == mode) return
        _state.update { it.copy(viewMode = mode) }
        viewModelScope.launch { saveViewMode(mode) }
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

            is CrewRunState.JoinChoice -> if (s.postId == postId) {
                startAt = null
                _state.update {
                    it.copy(
                        phase = CrewRunPhase.JOIN_CHOICE,
                        countdown = null,
                        untilStart = Duration.ZERO,
                        departedCount = s.departedCount
                    )
                }
            }

            // 카운트다운 후보에서 빠졌다(준비 취소): 대기실로 돌아간다.
            is CrewRunState.Lobby -> if (s.postId == postId) {
                startAt = null
                _state.update {
                    if (it.phase == CrewRunPhase.COUNTDOWN || it.phase == CrewRunPhase.JOIN_CHOICE) {
                        it.copy(phase = CrewRunPhase.LOBBY, countdown = null)
                    } else {
                        it
                    }
                }
                tick()
            }

            is CrewRunState.Running -> if (s.postId == postId) {
                // 대기실 응답 없이 진행에 들어왔으면(진행 중 앱을 다시 띄움) 크루원 표시 성별을 결과 응답에서 한 번 읽는다(계약 v1.3).
                if (_state.value.lobby == null) readGendersOnce(postId)
                // 줄 순서는 서버가 준 대기실 입장 순 그대로다(순위가 없다).
                _state.update { st ->
                    st.copy(
                        phase = CrewRunPhase.RUNNING,
                        countdown = null,
                        isJoining = false,
                        relay = s.entries.map { e ->
                            RelayRow(
                                e.name,
                                e.distance,
                                MateState.of(e.state.name),
                                e.isMe,
                                st.lobby.genderOf(e.memberId) ?: e.memberId?.let(rejoinGenders::get),
                                e.memberId
                            )
                        },
                        totalDistance = s.totalDistance,
                        mine = s.mine,
                        // 순위와 가장 가까운 사람은 내 측정 거리(중계보다 새 값)와 크루원 중계 거리로 센다([임시:D-성-267]).
                        standing = CrewRunStanding.of(
                            s.mine.distance.meters.toInt(),
                            s.entries.filter { e -> !e.isMe }.map { e ->
                                CrewRunStanding.Mate(e.memberId, e.name, e.distance.meters.toInt(), e.state)
                            }
                        )
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

    /**
     * 진행 중 앱을 다시 띄워 대기실에 들어가지 못하면(이미 시작) 중계에 크루원 성별이 없어 남자 기본 캐릭터가 된다. 결과 응답(진행 중에도
     * participants에 memberId·shownGender가 있다)을 한 번 읽어 다음 중계부터 채운다. 실패하면 기본 캐릭터 그대로다.
     */
    private fun readGendersOnce(postId: Long) {
        if (genderJob != null) return
        genderJob = viewModelScope.launch {
            val result = (crews.crewRunResult(postId) as? Outcome.Success)?.value ?: return@launch
            // 다음 중계(1초마다)부터 이 성별로 그린다.
            rejoinGenders = result.participants
                .mapNotNull { p -> p.memberId?.let { id -> p.shownGender?.let { id to it } } }
                .toMap()
            // 칩의 크루런 거리도 결과 응답에서 채운다(대기실 응답이 없다).
            _state.update { if (it.lobby == null) it.copy(distance = result.distance) else it }
        }
    }

    /**
     * 대기실에 들어온 참가자(나 포함)의 셀카 얼굴 부위 그림을 얼굴 키마다 한 번 받는다(D-644, 결과 화면 readMateFaces와 같은
     * 방식). 미입장은 점선 원이라 받지 않고, 들어오면 다음 대기실 응답에서 받는다. 받지 못하거나 빈 목록이면 얼굴 키로 기본 얼굴을 그린다.
     */
    private fun readLobbyFaces(lobby: Lobby) {
        val keys = lobby.participants.filter {
            it.isEntered
        }.mapNotNull { it.faceKey }.filter { requestedFaces.add(it) }
        // 받아 둔 그림(내 얼굴, 앞 화면에서 본 크루원)은 이번 대기실 응답과 함께 바로 그린다(기본 머리를 거치지 않게).
        val cached = keys.mapNotNull { key ->
            crews.cachedFaceLayers(key)?.takeIf { it.isNotEmpty() }?.let { key to it }
        }
        if (cached.isNotEmpty()) _state.update { it.copy(mateFaces = it.mateFaces + cached) }
        keys.filter { key -> cached.none { it.first == key } }.forEach { key ->
            viewModelScope.launch {
                when (val r = crews.faceLayers(key)) {
                    is Outcome.Success -> r.value.takeIf { it.isNotEmpty() }?.let { layers ->
                        _state.update { it.copy(mateFaces = it.mateFaces + (key to layers)) }
                    }

                    // 일시 실패면 다음 대기실 응답에서 다시 받는다(빈 목록, 기본 캐릭터 얼굴은 다시 받지 않는다).
                    is Outcome.Failure -> requestedFaces.remove(key)
                }
            }
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
                // 남은 초는 올림(3·2·1)이다. startAt은 이미 3초를 더한 측정 시작 시각이라 다시 더하지 않는다.
                countdown = startAt?.takeIf { s.phase == CrewRunPhase.COUNTDOWN }
                    ?.let {
                        (
                            Duration.between(
                                now,
                                it
                            ).toMillis() + SECOND_MILLIS - 1
                            ).floorDiv(SECOND_MILLIS).coerceAtLeast(0)
                    }
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
        const val SECOND_MILLIS = 1_000L
        const val TICK_MILLIS = 1_000L
        const val HINT_MILLIS = 2_500L
    }
}

/** 입장(enter)을 보낼 수 있는 대기실 단계(옛 서버면 단계 없음). 출발 뒤에는 enter 대신 참여(join)다. */
private fun Lobby.isEnterable(): Boolean = phase == null || phase == LobbyPhase.LOBBY || phase == LobbyPhase.COUNTDOWN

/**
 * 새 대기실 API가 없는 옛 서버의 실패인가: 서버 오류(5xx), 경로 없음(404 NOT_FOUND·HTTP_404), 메서드 없음(405). 이때만 옛 enter·토글로
 * 되돌아간다. 업무 거부(409 등)는 그대로 안내한다.
 */
private fun AppError.isMissingApi(): Boolean =
    this is AppError.Server || (this is AppError.Rejected && code in MISSING_API_CODES)

private val MISSING_API_CODES = setOf("NOT_FOUND", "HTTP_404", "HTTP_405")

/** 대기실 응답의 내 줄(me) 준비 여부. 내 줄이 없으면 null. */
private fun Lobby.myReady(): Boolean? = participants.firstOrNull { it.isMe }?.isReady

/** 대기실 참가자의 아바타 모습(memberId별, [임시:D-성-254]). 모습이나 회원 ID가 없는 줄은 뺀다. */
private fun Lobby.looks(): Map<Long, RunnerLook> =
    participants.mapNotNull { p -> p.memberId?.let { id -> p.look?.let { id to it } } }.toMap()

/** 중계 줄 회원의 표시 성별(대기실 응답의 같은 memberId 줄, 크루원 아바타). 중계에는 아바타 값을 싣지 않는다(계약 crewrun-sum v1.2). */
private fun Lobby?.genderOf(memberId: Long?): Gender? =
    memberId?.let { id -> this?.participants?.firstOrNull { it.memberId == id }?.shownGender }
