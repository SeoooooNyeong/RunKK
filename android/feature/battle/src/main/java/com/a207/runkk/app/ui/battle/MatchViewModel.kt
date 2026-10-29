package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 매칭 대기·성사(FR-080~FR-087, FR-227~FR-230, D-269). 대기 종류(랜덤 매칭, 친구 초대, 받은 초대 참가)는 [MatchTicket]이 정하고
 * 대기 → 성사 → 자동 출발 흐름은 하나다: 실시간 채널을 열어 구독·연결한 뒤 ticket.open으로 대기를 열고, 끝 메시지(MatchFound 등)를
 * 기다린다. 성사되면 BattleRunCoordinator.onMatched로 넘기고 조정자 상태(Matched → Countdown → Running)를 따라 보인다.
 * 실시간 채널은 지난 메시지를 다시 주지 않으므로 참가 요청·초대 전에 연결해야 MatchFound를 놓치지 않는다(D-274).
 * 거부·연결 끊김·끝난 초대는 [MatchUiState.notice] 안내를 띄우고 확인하면 이전 화면으로 간다.
 * 성사 뒤 출발 시각 15초가 지나도(출발 시각을 모르면 성사 30초 뒤) 측정이 시작되지 않으면 그만두기를 보인다(배틀 QA 2번).
 * 화면을 떠나면(ViewModel이 지워지면) ticket.abandon이 서버를 정리한다. 카카오톡 공유로 앱이 잠시 뒤로 가는 것은 떠남이 아니다(D-271).
 */
@HiltViewModel
class MatchViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator,
    private val channels: LiveChannelProvider,
    private val clock: Clock,
    private val consent: LocationConsentUseCase,
    private val account: AccountRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MatchUiState())
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private val _events = Channel<MatchEvent>(Channel.BUFFERED)
    val events: Flow<MatchEvent> = _events.receiveAsFlow()

    private var ticket: MatchTicket? = null
    private var joinToken: String? = null
    private var started = false
    private var channel: LiveChannel? = null
    private var waitJob: Job? = null
    private var tickJob: Job? = null
    private var followJob: Job? = null
    private var countdownJob: Job? = null
    private var stuckJob: Job? = null
    private var joinCheckJob: Job? = null
    private var consentJob: Job? = null
    private var handedOver = false
    private var navigated = false
    private var forfeiting = false
    private var startAt: Instant? = null
    private var inviterName: String = ""

    /**
     * 대기 채널에서 받은 출발 예약. 서버는 MatchFound와 BattleStart를 잇따라 보내는데 실시간 채널은 지난 메시지를 다시 주지 않아,
     * 조정자가 성사 뒤에 구독을 붙이면 BattleStart를 놓친다. 대기 중에 미리 받아 두었다가 성사 뒤 조정자에 넘긴다.
     */
    private var startsJob: Job? = null
    private var waitingLease: LiveChannel? = null
    private var earlyStart: BattleStart? = null
    private var matchedBattleId: Long? = null

    /**
     * 랜덤 매칭(FR-080): 배틀런 화면에서 고른 [distance]로 요청한다. 이미 대기열에 있으면(앱을 다시 연 경우) 다시 요청하지 않고
     * 대기 화면을 잇는다. 랜덤 대결 거리(1·3·5·10km)가 아니면 요청하지 않고 안내한다.
     */
    fun startQuick(distance: Distance) {
        if (!claimStart()) return
        _state.update { it.copy(distance = distance, opponentKind = OpponentKind.RANDOM) }
        if (distance !in MatchUiState.DISTANCES) {
            showNotice(UiText.Res(R.string.battle_error_distance_invalid))
            return
        }
        viewModelScope.launch {
            val status = (battles.matchStatus() as? Outcome.Success)?.value
            val isWaiting = status?.isWaiting == true
            val resumed = status?.distance?.takeIf { isWaiting }
            if (resumed != null) _state.update { it.copy(distance = resumed) }
            val waitingPhase = if (isWaiting) MatchPhase.WAITING else MatchPhase.REQUESTING
            begin(RandomTicket(resumed ?: distance, coordinator), waitingPhase, isResumed = isWaiting)
        }
    }

    /** 친구 대결 초대(FR-227): 채널 연결 → 초대 만들기 → 카카오톡 공유(ShareInvite). */
    fun startInvite(distance: Distance) {
        if (!claimStart()) return
        _state.update { it.copy(distance = distance, opponentKind = OpponentKind.FRIEND) }
        viewModelScope.launch {
            inviterName = (account.profile(AccountRepository.SELF) as? Outcome.Success)?.value?.displayName.orEmpty()
        }
        begin(FriendInviteTicket(distance, coordinator), MatchPhase.REQUESTING)
    }

    /**
     * 받은 초대(FR-229, D-273). 참가 확인 정보를 읽어 blockedReason이 없으면 참가 확인(CONFIRMING)을 보이고, 있으면 그 사유 안내를
     * 보인다. 서버가 막지 않아도 이 기기가 이미 대결 중이거나 측정 중이면 참가할 수 없다.
     */
    fun startJoin(token: String) {
        if (!claimStart()) return
        joinToken = token
        _state.update { it.copy(phase = MatchPhase.CONFIRMING, opponentKind = OpponentKind.FRIEND) }
        viewModelScope.launch {
            when (val read = battles.friendInvite(token)) {
                is Outcome.Success -> {
                    val view = read.value
                    _state.update { it.copy(distance = view.distance) }
                    val blocked = view.blockedReason ?: ALREADY_IN_BATTLE.takeIf { coordinator.isBusy() }
                    if (blocked !=
                        null
                    ) {
                        showNotice(joinNotice(AppError.Rejected(blocked)))
                    } else {
                        _state.update { it.copy(invite = view) }
                    }
                }

                is Outcome.Failure -> showNotice(joinNotice(read.error))
            }
        }
    }

    /** 참가하기(D-274): 위치 동의(D-267)를 확인한 뒤 채널을 연결하고 참가를 보낸다. */
    fun onAccept() {
        val token = joinToken ?: return
        if (_state.value.phase != MatchPhase.CONFIRMING || _state.value.invite == null) return
        if (!consent.isGranted()) {
            pendingConsent = ::onAccept
            _state.update { it.copy(isConsentSheetVisible = true) }
            return
        }
        begin(FriendJoinTicket(token, battles), MatchPhase.JOINING)
    }

    /** 거절(D-273): 거절을 보내고 홈으로. 거부되면(이미 끝난 초대 등) 그 사유 안내 뒤 홈. */
    fun onDecline() {
        val token = joinToken ?: return
        if (_state.value.phase != MatchPhase.CONFIRMING) return
        viewModelScope.launch {
            when (val declined = battles.declineFriendInvite(token)) {
                is Outcome.Success -> _events.send(MatchEvent.Exit)
                is Outcome.Failure -> showNotice(joinNotice(declined.error))
            }
        }
    }

    /** 카카오톡으로 다시 보내기(D-271): 같은 초대로 다시 공유한다. 만료를 늘리지 않는다. */
    fun onResend() {
        if (_state.value.phase != MatchPhase.INVITING) return
        val invite = (ticket as? FriendInviteTicket)?.invite ?: return
        _events.trySend(MatchEvent.ShareInvite(invite, inviterName))
    }

    /** 카카오톡도 웹 공유도 열지 못했다(D-270): 초대를 취소하고 안내 뒤 배틀런 화면으로. */
    fun onShareFailed() {
        if (_state.value.phase != MatchPhase.INVITING) return
        ticket?.abandon()
        stopWaiting()
        showNotice(UiText.Res(R.string.battle_invite_share_failed))
    }

    /** 초대가 끝난 안내(만료 "다시 초대하기", 거절 "확인")를 누르면 배틀런 화면 친구 분절로 돌아간다. */
    fun onInviteEndedConfirmed() {
        if (_state.value.phase == MatchPhase.INVITE_ENDED) _events.trySend(MatchEvent.Exit)
    }

    /** 안내 확인: 이전 화면으로. */
    fun onNoticeConfirmed() {
        _state.update { it.copy(notice = null) }
        _events.trySend(MatchEvent.Exit)
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    private var pendingConsent: (() -> Unit)? = null

    /** 대기를 여는 요청이 위치 동의로 거부돼 띄운 시트인가. 닫거나 동의가 실패하면 머물 단계가 없어 화면을 떠난다. */
    private var isRefusedConsent = false

    /** 위치 동의 시트 "동의하고 시작": 동의를 주고 누른 동작을 이어 간다. 실패하면 안내만 한다. */
    fun onConsentAgreed() {
        if (consentJob?.isActive == true) return
        _state.update { it.copy(isConsentSheetVisible = false) }
        consentJob = viewModelScope.launch {
            val next = pendingConsent
            pendingConsent = null
            val refused = isRefusedConsent
            isRefusedConsent = false
            when (val given = consent.give()) {
                is Outcome.Success -> next?.invoke()

                is Outcome.Failure ->
                    if (refused) {
                        showNotice(BattleErrorText.of(given.error))
                    } else {
                        _state.update { it.copy(error = BattleErrorText.of(given.error)) }
                    }
            }
        }
    }

    fun onConsentClosed() {
        pendingConsent = null
        _state.update { it.copy(isConsentSheetVisible = false) }
        if (isRefusedConsent) {
            isRefusedConsent = false
            _events.trySend(MatchEvent.Exit)
        }
    }

    /**
     * 취소(FR-085, FR-228). 랜덤 대기는 대기열에서 빼고(이미 배정되면 성사를 기다린다), 초대 대기는 초대를 취소한다. 받은 사람이 그 사이
     * 참가했으면(INVITE_ALREADY_JOINED) 취소를 접고 성사를 기다린다. 참가 확인과 초대 끝 안내는 그냥 떠난다.
     */
    fun onCancel() {
        when (_state.value.phase) {
            MatchPhase.WAITING -> cancelQueue()

            MatchPhase.INVITING -> cancelInvite()

            // 연결·요청 중에 떠나면 서버 대기를 정리한다(랜덤은 요청 뒤 대기열 취소, 초대는 만든 뒤 취소).
            MatchPhase.REQUESTING -> {
                ticket?.abandon()
                stopWaiting()
                _events.trySend(MatchEvent.Exit)
            }

            MatchPhase.CONFIRMING, MatchPhase.INVITE_ENDED -> _events.trySend(MatchEvent.Exit)

            // 요청 중·참가 중·성사 뒤에는 뒤로 가지 않는다(자동 출발, FR-087).
            else -> Unit
        }
    }

    /** 성사 뒤 측정이 늦어져 보인 그만두기(배틀 QA 2번). 포기를 보내고 이 회를 닫으면 이전 화면으로 돌아간다. */
    fun onForfeit() {
        if (!_state.value.canForfeit) return
        forfeiting = true
        _state.update { it.copy(canForfeit = false) }
        coordinator.forfeit()
    }

    private fun claimStart(): Boolean {
        if (started) return false
        started = true
        return true
    }

    private fun cancelQueue() {
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

    private fun cancelInvite() {
        val invite = (ticket as? FriendInviteTicket)?.invite ?: return
        _state.update { it.copy(phase = MatchPhase.CANCELLING) }
        viewModelScope.launch {
            val cancelled = coordinator.cancelInvite(invite.inviteId).await()
            if (_state.value.phase != MatchPhase.CANCELLING) return@launch
            val isJoined = cancelled is Outcome.Failure && cancelled.error == AppError.Rejected(INVITE_ALREADY_JOINED)
            if (isJoined) {
                _state.update { it.copy(phase = MatchPhase.INVITING) }
            } else {
                stopWaiting()
                _events.trySend(MatchEvent.Exit)
            }
        }
    }

    /**
     * 대기를 연다: 채널을 열어 출발 예약·끝 메시지를 먼저 구독하고 연결한 뒤 [MatchTicket.open]을 보낸다. [isResumed]면 서버에
     * 이미 대기가 있어 다시 열지 않는다.
     */
    private fun begin(next: MatchTicket, phase: MatchPhase, isResumed: Boolean = false) {
        ticket = next
        _state.update { it.copy(phase = phase, opponentKind = next.opponentKind, error = null, waitingSeconds = 0) }
        val opened = channels.open().also { channel = it }
        collectStarts(opened)
        if (phase == MatchPhase.WAITING) startTicking()
        waitJob = viewModelScope.launch {
            when (val end = awaitWaitEnd(opened, next, isResumed)) {
                is WaitEnd.Found -> onMatched(end.match)

                is WaitEnd.Failed -> onMatchFailed(end.failed)

                WaitEnd.Declined -> endInvite(MatchUiState.InviteEnd.DECLINED)

                WaitEnd.Expired -> endInvite(MatchUiState.InviteEnd.EXPIRED)

                is WaitEnd.Refused -> onRefused(end.error)

                // 연결 실패·채널 종료: 서버 대기를 정리하고 안내한다(앱을 죽이지 않는다).
                is WaitEnd.Lost -> failWaiting(end.error)
            }
        }
    }

    /**
     * 대기가 끝나는 첫 사건을 값으로 돌려준다(M-4: 예상된 연결 실패를 예외로 흐름 제어하지 않는다). 메시지 구독을 먼저 붙이고 연결이
     * 끝난 뒤 대기를 연다. 대기가 열리면 종류별 후속(초대 공유, 만료 타이머, 참가 확인 타이머)을 건다.
     */
    private suspend fun awaitWaitEnd(opened: LiveChannel, next: MatchTicket, isResumed: Boolean): WaitEnd =
        coroutineScope {
            val ends = Channel<WaitEnd>(Channel.CONFLATED)
            val listening = launch(start = CoroutineStart.UNDISPATCHED) {
                val end = opened.messages
                    .mapNotNull(next::endOf)
                    .onCompletion { cause -> if (cause == null) emit(WaitEnd.Lost(null)) }
                    .first()
                ends.trySend(end)
            }
            val opening = launch {
                val connected = opened.connect()
                if (connected is Outcome.Failure) {
                    ends.trySend(WaitEnd.Lost(connected.error))
                    return@launch
                }
                if (!isResumed) {
                    val result = next.open()
                    if (result is Outcome.Failure) {
                        ends.trySend(WaitEnd.Refused(result.error))
                        return@launch
                    }
                }
                onOpened(next)
                next.deadline?.let { deadline ->
                    delay(untilExpiry(deadline).plus(EXPIRY_GRACE).toMillis())
                    ends.trySend(WaitEnd.Expired)
                }
            }
            ends.receive().also {
                listening.cancel()
                opening.cancel()
            }
        }

    /**
     * 서버 만료 시각까지 남은 시간. 기기 시계가 서버와 크게 어긋나 남은 시간이 0보다 작거나 초대 수명(5분)보다 길면 만든 때부터
     * 5분으로 잰다(시계가 앞선 기기에서 바로 끝나지 않게).
     */
    private fun untilExpiry(deadline: Instant): Duration {
        val left = Duration.between(clock.instant(), deadline)
        return if (left.isNegative || left > INVITE_LIFETIME) INVITE_LIFETIME else left
    }

    /** 대기가 열렸다: 랜덤은 대기, 초대는 초대 대기와 공유, 참가는 MatchFound 확인 타이머. */
    private fun onOpened(opened: MatchTicket) {
        when (opened) {
            is RandomTicket -> _state.update {
                if (it.phase ==
                    MatchPhase.REQUESTING
                ) {
                    it.copy(phase = MatchPhase.WAITING)
                } else {
                    it
                }
            }

            is FriendInviteTicket -> {
                _state.update { it.copy(phase = MatchPhase.INVITING, waitingSeconds = 0) }
                opened.invite?.let { _events.trySend(MatchEvent.ShareInvite(it, inviterName)) }
            }

            is FriendJoinTicket -> watchJoined(opened)
        }
        startTicking()
    }

    /**
     * 참가가 받아들여졌는데 [JOIN_CONFIRM] 안에 MatchFound가 오지 않으면 진행 중인 대결을 확인한다(D-274): 그 대결이 있으면 그 값으로
     * 성사 흐름에 합류하고, 없으면 실패 안내 뒤 홈이다.
     */
    private fun watchJoined(joined: FriendJoinTicket) {
        joinCheckJob?.cancel()
        joinCheckJob = viewModelScope.launch {
            delay(JOIN_CONFIRM.toMillis())
            if (_state.value.phase != MatchPhase.JOINING) return@launch
            val active = (battles.active() as? Outcome.Success)?.value
            if (active == null || active.battleId != joined.battleId) {
                waitJob?.cancel()
                stopWaiting()
                showNotice(UiText.Res(R.string.battle_match_start_failed))
                return@launch
            }
            waitJob?.cancel()
            val match = MatchFound(
                battleId = active.battleId,
                opponentName = active.opponentName.orEmpty(),
                opponentTier = _state.value.invite?.inviter?.tier.orEmpty(),
                distance = active.distance,
                myBest = null,
                opponentBest = null,
                animationSeconds = 0
            )
            onMatched(match)
            active.startAt?.let { forwardStart(BattleStart(active.battleId, it, opponentTrack = null)) }
        }
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                _state.update {
                    if (it.phase in TICKING) it.copy(waitingSeconds = it.waitingSeconds + 1) else it
                }
            }
        }
    }

    /** 대기를 여는 요청이 거부됐다. 위치 동의가 없으면 시트(동의하면 다시 연다), 그 밖에는 사유 안내 뒤 이전 화면이다. */
    private fun onRefused(error: AppError) {
        closeWaiting()
        val code = (error as? AppError.Rejected)?.code
        val current = ticket
        ticket = null
        if (code == LOCATION_CONSENT_REQUIRED && current != null && current !is FriendJoinTicket) {
            pendingConsent = { begin(current, MatchPhase.REQUESTING) }
            isRefusedConsent = true
            _state.update { it.copy(isConsentSheetVisible = true) }
            return
        }
        showNotice(if (current is FriendJoinTicket) joinNotice(error) else refusedNotice(error))
    }

    /**
     * 대기 채널을 열지 못했다: 서버 대기를 정리하고(랜덤은 대기열 취소, 초대는 초대 취소, 조정자가 applicationScope에서 보낸다) 안내한다.
     * 인증이 풀렸으면(/ws 401, 갱신 실패) 로그인 안내, 그 밖에는 연결 끊김 안내다.
     */
    private fun failWaiting(error: AppError?) {
        ticket?.abandon()
        ticket = null
        closeWaiting()
        val text = if (error == AppError.Unauthorized) {
            BattleErrorText.of(AppError.Unauthorized)
        } else {
            UiText.Res(R.string.battle_match_connection_lost)
        }
        showNotice(text)
    }

    /**
     * 매칭 실패(MatchFailed): 서버가 이미 대기열에서 뺐으므로 취소를 보내지 않고 채널을 닫은 뒤 안내한다.
     * 사유 코드를 아는 문구가 있으면 그 문구, 모르면 서버 문구를 보인다.
     */
    private fun onMatchFailed(failed: MatchFailed) {
        closeWaiting()
        ticket = null
        val text = BattleErrorText.REJECTED[failed.code]?.let { UiText.Res(it) }
            ?: failed.message.takeIf { it.isNotBlank() }?.let { UiText.Plain(it) }
            ?: BattleErrorText.of(AppError.Rejected(failed.code))
        showNotice(text)
    }

    /** 초대가 거절·만료로 끝났다(D-271). 서버가 이미 끝냈으므로 취소를 보내지 않는다. */
    private fun endInvite(end: MatchUiState.InviteEnd) {
        closeWaiting()
        ticket = null
        _state.update { it.copy(phase = MatchPhase.INVITE_ENDED, inviteEnd = end) }
    }

    /**
     * 성사: 대결은 이제 조정자가 갖는다. 먼저 받아 둔 출발 예약이 있으면 넘긴다. 대기 임대는 바로 닫지 않는다: 조정자가 자기 임대를 열기 전에
     * 닫으면 임대 수가 0이 되어 연결이 끊기고 출발 예약을 잃는다. 측정이 시작되거나 대결이 끝나면 반납한다(D-196, 7차 AN7-1).
     */
    private fun onMatched(match: MatchFound) {
        tickJob?.cancel()
        joinCheckJob?.cancel()
        handedOver = true
        waitingLease = channel
        channel = null
        _state.update { it.copy(phase = MatchPhase.MATCHED, match = match, distance = match.distance, error = null) }
        coordinator.onMatched(match)
        matchedBattleId = match.battleId
        watchStuck(clock.instant().plus(MATCHED_STUCK))
        earlyStart?.let(::forwardStart)
        follow(match)
    }

    /** [deadline]까지 측정이 시작되지 않으면 그만두기를 보인다. 새 기한이 오면(출발 예약) 다시 잰다. */
    private fun watchStuck(deadline: Instant) {
        stuckJob?.cancel()
        stuckJob = viewModelScope.launch {
            val wait = Duration.between(clock.instant(), deadline)
            if (!wait.isNegative) delay(wait.toMillis())
            _state.update { if (it.phase == MatchPhase.MATCHED && !navigated) it.copy(canForfeit = true) else it }
        }
    }

    /** 대기 채널의 BattleStart를 받는다. 연결 전에 구독해야 MatchFound 직후에 오는 출발 예약도 받는다. */
    private fun collectStarts(opened: LiveChannel) {
        startsJob?.cancel()
        earlyStart = null
        matchedBattleId = null
        startsJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            opened.messages.filterIsInstance<BattleStart>().collect { start ->
                earlyStart = start
                forwardStart(start)
            }
        }
    }

    /** 성사된 대결의 출발 예약이면 조정자에 넘긴다. 조정자가 직접 받은 것과 겹쳐도 한 번만 시작한다(BattleRun.schedule). */
    private fun forwardStart(start: BattleStart) {
        if (start.battleId != matchedBattleId) return
        coordinator.onStart(start)
        startsJob?.cancel()
    }

    /** 성사 뒤 쥐고 있던 대기 임대를 반납한다. 조정자 임대가 연결을 이어 가므로 연결은 닫히지 않는다. */
    private fun releaseWaitingLease() {
        startsJob?.cancel()
        waitingLease?.close()
        waitingLease = null
    }

    /** 조정자 상태를 따라 자동 출발까지 남은 초를 보이고, 측정이 시작되면 대결 진행 화면으로 간다. */
    private fun follow(match: MatchFound) {
        followJob?.cancel()
        followJob = viewModelScope.launch {
            coordinator.state.collect { s ->
                when (s) {
                    is BattleRunState.Countdown -> if (s.battleId == match.battleId) countdownTo(s)

                    is BattleRunState.Running -> if (s.battleId == match.battleId) {
                        releaseWaitingLease()
                        started(match)
                    }

                    is BattleRunState.Finished -> if (s.battleId == match.battleId && !navigated) endedBeforeStart(s)

                    else -> Unit
                }
            }
        }
    }

    /**
     * 시작 전에 끝남(연결 실패·시작 거부·그만두기). 결과가 없으니 안내하고 이전 화면으로 돌아간다. 그만두기로 끝났으면 안내 없이 떠나고
     * (보고가 끝내 실패했을 때만 그 사유), 출발 전에 상대가 그만두면 서버가 대결을 취소하고 OPPONENT_FORFEITED를 보낸다(결과 없음).
     */
    private fun endedBeforeStart(s: BattleRunState.Finished) {
        releaseWaitingLease()
        val reason = coordinator.failure.value
        countdownJob?.cancel()
        stuckJob?.cancel()
        waitJob?.cancel()
        handedOver = false
        val fallback = when {
            forfeiting -> null
            s.status == BattleEnded.Status.OPPONENT_FORFEITED -> UiText.Res(R.string.battle_match_opponent_left)
            else -> UiText.Res(R.string.battle_match_start_failed)
        }
        val text = reason?.let(BattleErrorText::of) ?: fallback
        forfeiting = false
        coordinator.acknowledgeFinished()
        followJob?.cancel()
        if (text == null) _events.trySend(MatchEvent.Exit) else showNotice(text)
    }

    private fun countdownTo(s: BattleRunState.Countdown) {
        startAt = s.startAt
        watchStuck(s.startAt.plus(START_STUCK))
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

    private fun started(match: MatchFound) {
        if (navigated) return
        navigated = true
        countdownJob?.cancel()
        stuckJob?.cancel()
        _events.trySend(
            MatchEvent.Started(
                Routes.BattleRunning(
                    match.battleId,
                    match.opponentName,
                    match.distance.meters,
                    _state.value.opponentKind.name,
                    startAtEpochMillis = startAt?.toEpochMilli()
                )
            )
        )
    }

    private fun showNotice(text: UiText) = _state.update { it.copy(notice = text) }

    /** 대기 채널과 시계를 정리한다(서버 정리는 하지 않는다). */
    private fun closeWaiting() {
        tickJob?.cancel()
        startsJob?.cancel()
        joinCheckJob?.cancel()
        channel?.close()
        channel = null
    }

    private fun stopWaiting() {
        waitJob?.cancel()
        closeWaiting()
        ticket = null
    }

    override fun onCleared() {
        // 성사 전에 화면을 떠나면(시스템 제스처·탭 이동으로 ViewModel이 지워지면) 대기 채널을 닫고 서버 대기를 정리한다.
        // viewModelScope는 이미 취소됐으므로 조정자가 applicationScope에서 보낸다(FR-085, D-271). 성사 뒤 채널·대결은 조정자가 갖는다.
        if (handedOver) {
            releaseWaitingLease()
            return
        }
        channel?.close()
        channel = null
        if (_state.value.phase in OPEN) ticket?.abandon()
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val ALREADY_IN_BATTLE = "ALREADY_IN_BATTLE"
        const val INVITE_ALREADY_JOINED = "INVITE_ALREADY_JOINED"
        const val LOCATION_CONSENT_REQUIRED = "LOCATION_CONSENT_REQUIRED"

        /** 출발 예약을 받지 못한 채 성사 화면에 머무는 한도와, 출발 시각이 지나고도 측정이 시작되지 않는 한도(배틀 QA 2번). */
        val MATCHED_STUCK: Duration = Duration.ofSeconds(30)
        val START_STUCK: Duration = Duration.ofSeconds(15)

        /** 초대 만료 유예(D-271 기본값): 서버 만료 알림이 연결 끊김으로 사라져도 만료 + 2초에 끝낸다. */
        val EXPIRY_GRACE: Duration = Duration.ofSeconds(2)

        /** 초대 수명(서버 만료 = 만든 때 + 5분, D-271). */
        val INVITE_LIFETIME: Duration = Duration.ofMinutes(5)

        /** 참가 응답 뒤 MatchFound를 기다리는 한도(D-274). */
        val JOIN_CONFIRM: Duration = Duration.ofSeconds(10)

        /** 대기 시간을 세는 단계. */
        val TICKING = setOf(MatchPhase.WAITING, MatchPhase.INVITING)

        /** 서버에 대기가 열렸거나 열리는 중인 단계(떠나면 정리한다). */
        val OPEN = setOf(MatchPhase.REQUESTING, MatchPhase.WAITING, MatchPhase.INVITING, MatchPhase.CANCELLING)
    }
}

/** 받은 초대의 참가 막힘 사유 문구(D-273, FR-229 예외 흐름). 모르는 사유는 대결 오류 문구다. */
internal fun joinNotice(error: AppError): UiText = when ((error as? AppError.Rejected)?.code) {
    "INVITE_NOT_FOUND", "INVITE_EXPIRED", "INVITE_CANCELLED" -> UiText.Res(R.string.battle_join_ended)
    "INVITE_ALREADY_JOINED" -> UiText.Res(R.string.battle_join_already_joined)
    "INVITE_OWN" -> UiText.Res(R.string.battle_join_own)
    "ALREADY_IN_BATTLE" -> UiText.Res(R.string.battle_join_busy)
    else -> BattleErrorText.of(error)
}

/** 랜덤 매칭·초대 만들기 거부 문구(D-270). 초대 쪽 사유는 친구 대결 문구, 나머지는 대결 오류 문구다. */
internal fun refusedNotice(error: AppError): UiText = when ((error as? AppError.Rejected)?.code) {
    "INVITE_ALREADY_PENDING" -> UiText.Res(R.string.battle_invite_already_pending)
    "ALREADY_IN_BATTLE" -> UiText.Res(R.string.battle_invite_busy)
    else -> BattleErrorText.of(error)
}
