package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GpsReadiness
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.CrewChatPosted
import com.a207.runkk.app.domain.live.CrewChatReactionChanged
import com.a207.runkk.app.domain.live.CrewChatRemoved
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunChanged
import com.a207.runkk.app.domain.live.CrewRunDeparture
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.domain.live.FriendInviteDeclined
import com.a207.runkk.app.domain.live.FriendInviteExpired
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.run.CoordinatedRound
import com.a207.runkk.app.domain.run.GpsWarmUp
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceMessage
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.LocationFilter
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 대결 러닝의 측정과 실시간 메시지를 잇는다(6.1.14). 화면이 아니라 대결 한 회와 수명을 같이 한다:
 * onMatched·onRecordBattleStart 때 주입받은 applicationScope 아래에 자기 범위(SupervisorJob)를 만들고
 * endRun·forfeit 뒤 닫는다(5.1.2, design-review.md M44). 셀프런·셀럽런 그만두기는 기록을 버리고 닫는다(D-266). 상태 변경은 모두 그 범위의 직렬 디스패처에서 돈다.
 *
 * 상대 진행은 상대 종류마다 다른 OpponentFeed가 맡는다(리뷰 O-5): 랜덤 대결은 1초마다 진행값을 채널로 보내고 서버의
 * OpponentProgress·BattleGuidance를 반영하며, 기록 상대(고스트·나와의 싸움·유명인)는 OpponentTrack으로 상대 거리를 보간하고
 * GapTracker로 추월·도착을 판정한다(D-05). 종료 카운트다운(Closing)이 시작되면 진행 메시지가 그 상태를 덮지 않는다(F-1).
 * 서버 보고(시작·그만두기)를 다시 보내도 실패하거나 채널에 연결하지 못하면 failure로 알린다(F-3, O-6).
 *
 * ui.battle은 domain.run을 보지 않으므로(5.1.2) 내 측정값(RunTracker 진행값)을 상태의 MyProgress로 옮긴다. 앱 화면 잠금은 없다(D-256).
 */
@Singleton
class BattleRunCoordinator
@Inject
constructor(
    private val runTracker: RunTracker,
    private val liveChannelProvider: LiveChannelProvider,
    private val battleRepository: BattleRepository,
    private val guidanceOutput: GuidanceOutput,
    private val clock: Clock,
    private val applicationScope: CoroutineScope,
    private val gpsWarmUp: GpsWarmUp,
    private val pendingForfeits: PendingForfeitStore
) {
    private val _state = MutableStateFlow<BattleRunState>(BattleRunState.Idle)
    val state: StateFlow<BattleRunState> = _state.asStateFlow()

    private val _failure = MutableStateFlow<AppError?>(null)

    /**
     * 이 회에서 생긴 실패: 서버 보고(markStarted·forfeit)를 다시 보내도 실패했거나(F-3), 실시간 채널에 연결하지 못했거나(O-6),
     * 측정 시작이 거부됐다. 화면이 안내를 띄운다. 새 회를 열거나 acknowledgeFinished를 부르면 지운다.
     */
    val failure: StateFlow<AppError?> = _failure.asStateFlow()

    private val _liveState = MutableStateFlow<LiveState?>(null)

    /** 이 회의 실시간 채널 연결 상태(랜덤·고스트). 진행 화면이 연결 끊김을 보인다(배틀 QA 5번). 채널이 없는 회·닫힌 회는 null이다. */
    val liveState: StateFlow<LiveState?> = _liveState.asStateFlow()

    @Volatile
    private var run: BattleRun? = null

    /** 서버에 가 있는 매칭 요청(이탈 취소가 요청보다 먼저 가지 않게 기다린다). */
    @Volatile
    private var requestJob: Deferred<Outcome<MatchAcceptance>>? = null

    /** 서버에 가 있거나 만들어진 친구 초대(화면을 떠나면 취소한다). */
    @Volatile
    private var inviteJob: Deferred<Outcome<FriendInvite>>? = null

    /** 이탈 뒤 대기열 취소(재시도 포함). 새 요청이 오면 멈춘다. */
    @Volatile
    private var abandonJob: Job? = null

    /** 성사 뒤 출발 전 이탈의 그만두기(재시도 포함, [abandonMatched]). */
    @Volatile
    private var forfeitJob: Job? = null

    /** 남은 그만두기를 다시 보내는 작업([settlePendingForfeit]). 겹쳐 부르면 이 작업을 기다린다. */
    @Volatile
    private var settleJob: Job? = null

    /** 랜덤 대결: 진행값을 실시간 채널로 보내고 상대 진행은 서버 메시지로 받는다. */
    private val liveOpponentFeed =
        object : OpponentFeed {
            override fun onProgress(run: BattleRun, p: RunProgress) {
                run.channel?.send(
                    ProgressReport(
                        channel = ProgressReport.Channel.BATTLE,
                        channelId = run.battleId,
                        distanceMeters = p.distance.meters,
                        elapsedSeconds = p.elapsed.seconds,
                        sentAt = clock.instant()
                    )
                )
            }

            override fun hasArrived(run: BattleRun, mineMeters: Double): Boolean = mineMeters >= run.distance.meters
        }

    /**
     * 랜덤·고스트 매칭 성사. 매칭 화면·애니메이션은 ui.battle이 그린다(design-review.md M25).
     * [startDevice]는 매칭을 시작한 기기다. 워치에서 시작한 대결은 WATCH로 기록하고(FR-245, D-378) 폰 화면은 넘기지 않는다(PHONE).
     */
    fun onMatched(m: MatchFound, startDevice: RunStartSpec.StartDevice = RunStartSpec.StartDevice.PHONE) {
        // 성사된 친구 초대는 끝났다: 화면을 떠나도 취소하지 않는다(AN8-16).
        inviteJob = null
        val run = openRun(m.battleId, m.distance, m.opponentName, startDevice)
        run.round.scope.launch {
            _state.value = BattleRunState.Matched(m)
            warmUpGps(run)
            val channel = liveChannelProvider.open()
            run.attach(channel)
            run.round.scope.launch { channel.state.collect { _liveState.value = it } }
            // 연결 직후 오는 메시지(BattleStart 등)를 잃지 않게 연결 전에 구독한다.
            run.round.scope.launch(start = CoroutineStart.UNDISPATCHED) { channel.messages.collect { handle(run, it) } }
            val connected = channel.connect()
            if (connected is Outcome.Failure) fail(run, connected.error)
        }
    }

    /** 랜덤·고스트 시작 예약: startAt에 측정 시작. opponentTrack이 있으면 기록 상대 모드. */
    fun onStart(s: BattleStart) {
        val run = run ?: return
        run.round.scope.launch { start(run, s) }
    }

    /**
     * 나와의 싸움·유명인: 3초 카운트다운 뒤 측정을 시작하고 시작을 서버에 알린다(design-review.md M34).
     * [startDevice]는 [onMatched]와 같다(워치 시작은 WATCH, 폰은 기본값 PHONE).
     */
    fun onRecordBattleStart(
        s: RecordBattleStart,
        startDevice: RunStartSpec.StartDevice = RunStartSpec.StartDevice.PHONE
    ) {
        val run = openRun(s.battleId, s.distance, s.opponentName, startDevice)
        run.round.scope.launch {
            run.schedule(s.track, s.cardKind)
            _state.value = BattleRunState.Countdown(s.battleId, clock.instant().plus(RECORD_COUNTDOWN))
            delay(RECORD_COUNTDOWN.toMillis())
            if (begin(run)) report(run) { battleRepository.markStarted(s.battleId) }
        }
    }

    /**
     * 서버 연결 없이 미리 받은 상대 기록으로 셀프·셀럽런을 진행한다(D-377, 사용자 결정 W18). 진행은 기록 대결과 같다: 3초 카운트다운 뒤
     * 측정하고 구간 기록으로 상대 거리·격차·추월을 낸다(FR-098). 서버 대결이 없으므로 시작·그만두기·취소를 서버에 보고하지 않고,
     * 기록은 대결 ID 없는 자유런으로 확정해 보통 자유런처럼 보낸다. 상태(BattleRunState)의 battleId 자리는 0이다.
     */
    fun onOfflineRecordBattle(b: OfflineRecordBattle) {
        val run =
            openRun(
                OFFLINE_BATTLE_ID,
                b.distance,
                opponentName = null,
                RunStartSpec.StartDevice.WATCH,
                isOffline = true
            )
        run.round.scope.launch {
            run.schedule(b.track, CardKind.FREE_RUN)
            _state.value = BattleRunState.Countdown(OFFLINE_BATTLE_ID, clock.instant().plus(RECORD_COUNTDOWN))
            delay(RECORD_COUNTDOWN.toMillis())
            begin(run)
        }
    }

    /**
     * 진행값 한 개를 처리한다. 운영 코드는 RunTracker.progress 구독(CoordinatedRound)만 이 경로를 쓰고 화면은 부르지 않는다.
     * 시험이 늦게 처리된 진행값을 넣으려고 모듈 안(internal)에만 연다(1차 S-16). 같은 값은 한 번만 처리한다.
     */
    internal fun onProgress(p: RunProgress) {
        val run = run ?: return
        run.round.scope.launch { progress(run, p) }
    }

    fun onMessage(m: ServerMessage) {
        val run = run ?: return
        run.round.scope.launch { handle(run, m) }
    }

    /** 본인 도착 → 기록 저장. */
    fun onArrived() {
        val run = run ?: return
        run.round.scope.launch { finish(run, status = null) }
    }

    /**
     * 확인 뒤 그만두기(POST /battles/{id}/forfeit). 보고가 끝내 실패해도 기기 측정은 끝내고 failure로 알린다.
     * 모든 대결(셀프런·셀럽런 포함)에서 서버는 패배로 판정하고 기기는 지금까지 달린 기록을 저장해 Finished로 닫는다(사용자 요청 2026-10-30,
     * 예전에는 셀프런·셀럽런 그만두기가 기록을 버렸다). 기록 없이 끝내려면 [cancel]이다.
     */
    fun forfeit() = end { run ->
        report(run) { battleRepository.forfeit(run.battleId) }
        finish(run, status = null)
    }

    /**
     * 셀프런·셀럽런 취소(POST /battles/{id}/cancel, 출발 전 카운트다운 "다시 고르기" 포함): 서버는 승패 없이 대결을 취소하고 기기는 기록을
     * 버린 뒤 [BattleRunState.Discarded]로 닫는다(D-266, 사용자 요청 2026-10-30).
     */
    fun cancel() = end { run ->
        report(run) { battleRepository.cancel(run.battleId) }
        discard(run)
    }

    /** 그만두기·취소를 한 번만 한다(확인·다시 고르기 연타). 직렬 디스패처라 표시만으로 막힌다. */
    private fun end(block: suspend (BattleRun) -> Unit) {
        val run = run ?: return
        run.round.scope.launch {
            if (run.isForfeiting) return@launch
            run.isForfeiting = true
            block(run)
        }
    }

    /**
     * 친구 대결 초대 취소(DELETE /battles/friend-invites/{inviteId}, D-271). 화면이 사라져도 보내도록 applicationScope에서 보내고,
     * 네트워크 실패는 1·2초 뒤 두 번 더 보내며 거부(403, INVITE_NOT_FOUND, INVITE_ALREADY_JOINED)는 다시 보내지 않는다.
     * 결과를 기다릴 수 있다(취소 버튼은 INVITE_ALREADY_JOINED면 취소를 접고 MatchFound를 기다린다).
     */
    fun cancelInvite(inviteId: Long): Deferred<Outcome<Unit>> = applicationScope.async {
        var attempt = 1
        var result = battleRepository.cancelFriendInvite(inviteId)
        while (result is Outcome.Failure && result.error !is AppError.Rejected && attempt < CANCEL_ATTEMPTS) {
            delay(CANCEL_RETRY_MILLIS * attempt)
            result = battleRepository.cancelFriendInvite(inviteId)
            attempt++
        }
        result
    }

    /**
     * 친구 대결 초대 만들기(POST /battles/friend-invites, D-270). 매칭 요청처럼 applicationScope에서 보내 화면이 사라져도 응답을 받고,
     * 그 사이 화면이 떠났으면 [abandonInvite]가 만들어진 초대를 취소한다(남은 초대가 랜덤 매칭을 막지 않게, D-271).
     */
    suspend fun createInvite(distance: Distance): Outcome<FriendInvite> {
        // 짝지은 워치가 측정 중이면 초대를 만들지 않는다(FR-252, D-373 (2)): 상대가 참가한 뒤 카운트다운 끝에 거부되지 않게.
        if (runTracker.isWatchMeasuring) {
            return Outcome.Failure(AppError.Rejected(AppError.Rejected.WATCH_MEASURING))
        }
        val created = applicationScope.async { battleRepository.createFriendInvite(distance) }
        inviteJob = created
        return created.await()
    }

    /**
     * 초대 대기 화면을 떠났다(ViewModel이 지워짐, D-271). 만드는 중이면 끝난 뒤에, 이미 만들었으면 바로 그 초대를 [cancelInvite]로
     * 취소한다. 만들지 못했거나 떠날 초대가 없으면 아무것도 하지 않는다. 여러 번 불러도 한 번만 취소한다.
     */
    fun abandonInvite() {
        val pending = inviteJob ?: return
        inviteJob = null
        applicationScope.launch {
            val created = pending.awaitOrNull() as? Outcome.Success ?: return@launch
            cancelInvite(created.value.inviteId).await()
        }
    }

    /** 만료·거절로 끝난 초대를 잊는다(AN8-16). 화면을 떠나도 끝난 초대에 취소를 다시 보내지 않는다. */
    fun forgetInvite() {
        inviteJob = null
    }

    /**
     * 짝지은 워치가 스스로 측정 중인가(FR-252, D-373 (2)). 서버에 대결을 만들기 전(나와의 싸움·유명인 시작, 친구 초대 참가)에 화면이 읽어
     * 3초 카운트다운 뒤 RunTracker.startRun에서 거부되지 않게 한다.
     */
    val isWatchMeasuring: Boolean
        get() = runTracker.isWatchMeasuring

    /**
     * 열린 회의 대결 거리. 열린 회가 없으면 null. 워치가 성사~출발 사이에 화면을 다시 열 때 카운트다운 머리줄과 목표를 되살린다
     * (상태도 2.4.25, 코드 리뷰 WR12-2: Countdown 상태에는 거리가 없다).
     */
    val activeDistance: Distance?
        get() = run?.distance

    /** 대결에 참가할 수 없을 만큼 바쁜가: 열린 회가 있거나 자유런 등 측정 중이다(친구 대결 참가 전 확인, D-273). */
    fun isBusy(): Boolean = run != null || runTracker.isMeasuring

    /**
     * 랜덤 매칭 요청(POST /battles/match, FR-080). applicationScope에서 보내 화면이 사라져도 응답을 받는다. 앞선 이탈의 취소
     * 재시도가 남아 있으면 먼저 멈춰 새 대기열 등록을 지우지 않게 한다.
     */
    suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance> {
        // 짝지은 워치가 측정 중이면 대기열에 넣지 않는다(FR-252, D-373 (2)). 성사 뒤 측정 시작은 RunTracker.startRun이 다시 막는다.
        if (runTracker.isWatchMeasuring) {
            return Outcome.Failure(AppError.Rejected(AppError.Rejected.WATCH_MEASURING))
        }
        abandonJob?.cancelAndJoin()
        abandonJob = null
        // 남은 그만두기를 먼저 끝내 떠난 대결이 새 요청을 막지 않게 한다(S15P31A207-278).
        settlePendingForfeit()
        val request = applicationScope.async { battleRepository.requestMatch(distance) }
        requestJob = request
        return request.await()
    }

    /**
     * 매칭 대기 중 화면이 사라질 때(시스템 제스처·탭 이동으로 ViewModel이 지워질 때) 서버 대기열에서 뺀다(FR-085).
     * 화면 범위는 이미 취소됐으므로 applicationScope에서 보낸다. 요청이 아직 서버에 가 있으면 끝난 뒤에 보내고(취소가 먼저 도착해
     * 대기열에 남지 않게), 요청이 받아들여지지 않았으면 보내지 않는다. 네트워크 실패는 1·2초 뒤 두 번 더 보내고, 거부(이미 성사·
     * 대기 없음)는 다시 보내지 않는다. 이미 짝지어져(ALREADY_MATCHED) 거부되면 그 대결에 그만두기를 보낸다([forfeitMatched]).
     */
    fun abandonMatching() {
        val pending = requestJob
        requestJob = null
        abandonJob?.cancel()
        abandonJob = applicationScope.launch {
            if (pending != null && pending.awaitOrNull() !is Outcome.Success) return@launch
            var attempt = 1
            var result = battleRepository.cancel()
            while (result is Outcome.Failure && result.error !is AppError.Rejected && attempt < CANCEL_ATTEMPTS) {
                delay(CANCEL_RETRY_MILLIS * attempt)
                result = battleRepository.cancel()
                attempt++
            }
            val isMatched = result is Outcome.Failure && result.error == AppError.Rejected(ALREADY_MATCHED)
            // 새 매칭 요청(requestMatch)이 이 작업을 멈추고 기다릴 때도 그만두기는 끝까지 보낸다. 남은 대결이 새 요청을 막지 않게 한다.
            // 보내지 못하면(백그라운드 네트워크 제한) 기기에 남겨 앱이 앞으로 올 때 다시 보낸다.
            if (isMatched) {
                withContext(NonCancellable) {
                    pendingForfeits.save(PendingForfeit(battleId = null, since = clock.instant()))
                    forfeitMatched(battleId = null)
                }
            }
        }
    }

    /**
     * 성사 뒤 출발 전(측정 시작 전)에 앱이 뒤로 갔다(S15P31A207-278, 사용자 결정 2026-11-08 "출발 전이면 같은 규칙으로 기권"). 백그라운드에서는
     * 측정 시작(포그라운드 서비스)이 막히므로 이 회를 기록 없이 닫고([BattleRunState.Idle]) 그 대결에 그만두기를 보낸다. 출발 전이라 서버는
     * 승패 없이 대결을 취소한다. 그만두기는 먼저 기기에 남기고 보내며, 보내지 못하면 앱이 앞으로 올 때 다시 보낸다([settlePendingForfeit]).
     * [battleId] 회가 열려 있지 않거나 이미 측정 중이면 아무것도 하지 않고 false를 돌려준다.
     */
    fun abandonMatched(battleId: Long): Boolean {
        val current = run ?: return false
        if (current.battleId != battleId || current.round.isMeasuring) return false
        close(current, BattleRunState.Idle)
        pendingForfeits.save(PendingForfeit(battleId, clock.instant()))
        forfeitJob = applicationScope.launch { forfeitMatched(battleId) }
        return true
    }

    /**
     * 앱이 앞으로 왔다(또는 새 매칭 요청 전): 남은 그만두기가 있으면 다시 보낸다(S15P31A207-278). 보내는 중인 그만두기가 있으면 끝나기를
     * 기다린 뒤 아직 남았을 때만 보낸다. [PENDING_FORFEIT_LIFETIME]보다 오래된 것은 보내지 않고 지운다. 겹쳐 부르면 진행 중인 작업을 기다린다.
     */
    suspend fun settlePendingForfeit() {
        settleJob?.takeIf { it.isActive }?.let { return it.join() }
        val inFlight = listOfNotNull(abandonJob, forfeitJob).filter { it.isActive }
        if (inFlight.isEmpty() && pendingForfeits.load() == null) return
        val job =
            applicationScope.launch {
                inFlight.forEach { it.join() }
                val pending = pendingForfeits.load() ?: return@launch
                if (Duration.between(pending.since, clock.instant()) > PENDING_FORFEIT_LIFETIME) {
                    pendingForfeits.clear()
                    return@launch
                }
                forfeitMatched(pending.battleId)
            }
        settleJob = job
        job.join()
    }

    /**
     * 떠난 매칭의 대결에 그만두기를 보낸다(S15P31A207-278). 출발 전이라 서버는 승패 없이 대결을 취소하고 상대에게 OPPONENT_FORFEITED를
     * 보낸다. [battleId]를 모르면(대기열 취소가 ALREADY_MATCHED로 거부됨) 진행 중인 대결을 찾는다([findMatched]). 이 프로세스가 맡은
     * 대결이면 보내지 않는다. 그만두기의 네트워크 실패는 1·2초 뒤 두 번 더 보낸다. 보냈거나 거부됐거나 끝낼 대결이 없으면 남은 그만두기를
     * 지우고, 네트워크로 끝내 보내지 못하면(백그라운드 네트워크 제한 포함) 남겨 둔다.
     */
    private suspend fun forfeitMatched(battleId: Long?) {
        val target = battleId ?: findMatched() ?: return
        if (target == NO_BATTLE || isRunning(target)) {
            pendingForfeits.clear()
            return
        }
        // 번호를 알게 됐으면 남은 그만두기에 적어 다음에는 찾지 않고 보낸다.
        if (battleId == null) pendingForfeits.load()?.let { pendingForfeits.save(it.copy(battleId = target)) }
        var attempt = 1
        var result = battleRepository.forfeit(target)
        while (result is Outcome.Failure && result.error !is AppError.Rejected && attempt < CANCEL_ATTEMPTS) {
            delay(CANCEL_RETRY_MILLIS * attempt)
            result = battleRepository.forfeit(target)
            attempt++
        }
        if (result !is Outcome.Failure || result.error is AppError.Rejected) pendingForfeits.clear()
    }

    /**
     * 떠난 매칭으로 생긴 진행 중인 대결 번호. 서버가 짝을 잡고 대결을 만드는 사이에는 대결이 아직 없을 수 있어 1·2초 뒤 두 번 더 찾는다.
     * 매칭으로 생길 수 있는 대결(랜덤·고스트)만 돌려주고, 대결이 없거나 다른 종류면 [NO_BATTLE]이다. 끝내 읽지 못하면(네트워크) null이다.
     */
    private suspend fun findMatched(): Long? {
        var attempt = 1
        var active = battleRepository.active()
        var isRead = active is Outcome.Success
        while ((active as? Outcome.Success)?.value == null && attempt < CANCEL_ATTEMPTS) {
            delay(CANCEL_RETRY_MILLIS * attempt)
            active = battleRepository.active()
            isRead = isRead || active is Outcome.Success
            attempt++
        }
        val battle = (active as? Outcome.Success)?.value ?: return NO_BATTLE.takeIf { isRead }
        return if (battle.type in MATCHED_TYPES) battle.battleId else NO_BATTLE
    }

    /**
     * 이 프로세스가 [battleId] 대결을 맡고 있는가(열린 회가 있는가). 앱을 다시 켜 서버에만 남은 대결을 찾을 때 쓴다(배틀 QA 7번).
     */
    fun isRunning(battleId: Long): Boolean = run?.battleId == battleId

    /** 결과 화면이 Finished를(기록런 탭이 Discarded를) 받은 뒤 부른다. 다음 대결 화면이 지난 회의 종료 상태를 받지 않게 Idle로 돌린다(S-17). */
    fun acknowledgeFinished() {
        _state.update {
            if (it is BattleRunState.Finished ||
                it is BattleRunState.Discarded
            ) {
                BattleRunState.Idle
            } else {
                it
            }
        }
        _failure.value = null
    }

    /**
     * 성사 뒤 출발 전까지 GPS를 미리 켠다(W55, 실기기 시험에서 출발 뒤 41초 동안 위치가 없었다). 측정과 같은 정확도 기준(30m)의
     * 위치를 한 번 받으면 준비됨으로 두고, 상태는 성사·카운트다운 상태의 gps로 보인다. 측정을 시작하면([begin]) 끄고, 회가 닫히면
     * (취소·이탈·그만두기·연결 실패) 범위와 함께 꺼진다. 상한 시간이 지나거나 위치를 켤 수 없으면 끄고 상태를 지운다.
     */
    private fun warmUpGps(run: BattleRun) {
        run.warmUpJob =
            run.round.scope.launch {
                withTimeoutOrNull(GPS_WARM_UP_LIMIT.toMillis()) {
                    gpsWarmUp.readiness(LocationFilter.MAX_ACCURACY_METERS).collect { readiness ->
                        if (run.gps != GpsReadiness.READY) showGps(run, readiness)
                    }
                }
                showGps(run, null)
            }
    }

    /** 이 회의 예열 상태를 성사·카운트다운 상태에 싣는다. 다른 상태(측정 중·종료)는 그대로 둔다. */
    private fun showGps(run: BattleRun, readiness: GpsReadiness?) {
        run.gps = readiness
        _state.update { current ->
            when {
                current is BattleRunState.Matched && current.match.battleId == run.battleId ->
                    current.copy(gps = readiness)

                current is BattleRunState.Countdown && current.battleId == run.battleId ->
                    current.copy(gps = readiness)

                else -> current
            }
        }
    }

    private suspend fun start(run: BattleRun, s: BattleStart) {
        if (run.battleId != s.battleId) return
        if (!run.schedule(s.opponentTrack?.let(OpponentTrack::from), cardKind = null)) return
        _state.value = BattleRunState.Countdown(s.battleId, s.startAt, run.gps)
        val wait = Duration.between(clock.instant(), s.startAt)
        if (!wait.isNegative) delay(wait.toMillis())
        begin(run)
    }

    /**
     * 측정을 시작하고 진행값 구독을 붙인다. 시작이 거부되면(위치 동의 없음 등) 이 회를 닫는다.
     * 이미 이 회가 측정 중이면 아무것도 하지 않는다(재전송된 시작 신호로 진행 중인 회를 닫지 않는다).
     */
    private fun begin(run: BattleRun): Boolean {
        if (run.round.isMeasuring) return false
        val plan = PlanSnapshot(targetDistance = run.distance, targetPace = null)
        val spec =
            if (run.isOffline) {
                // 오프라인 기록 대결은 서버 대결이 없어 자유런으로 확정·전송한다(D-377). 워치에서만 연다(SQ-46, D-378).
                RunStartSpec(
                    mode = RunMode.FREE,
                    cardKind = CardKind.FREE_RUN,
                    startDevice = run.startDevice,
                    todayPlan = plan,
                    isOfflineBattle = true
                )
            } else {
                RunStartSpec(
                    mode = RunMode.BATTLE,
                    cardKind = run.cardKind,
                    startDevice = run.startDevice,
                    battleId = run.battleId,
                    todayPlan = plan,
                    opponentName = run.opponentName
                )
            }
        val started = run.round.begin(spec) { progress(run, it) }
        // 측정 서비스의 위치 구독이 이어받는다. 예열 위치는 기록에 넘기지 않았다(W55).
        run.stopGpsWarmUp()
        if (started is Outcome.Failure) {
            // 셀프런·셀럽런은 혼자 하는 대결이라, 측정을 못 시작하면(위치 권한 거절 등) 서버 대결을 취소해 진행 중으로 남기지 않는다
            // (S15P31A207-336). 상대가 있는 대결은 서버 규칙(시간 초과)에 맡긴다.
            if (!run.isOffline && run.cardKind in RECORD_BATTLE_KINDS) {
                applicationScope.launch { battleRepository.cancel(run.battleId) }
            }
            fail(run, started.error)
            return false
        }
        _state.value =
            BattleRunState.Running(run.battleId, gapMeters = null, opponentDistance = null, lastGuidance = null)
        return true
    }

    /** 서버 보고. 다시 보내도 실패하면 failure로 남기고 화면 안내를 낸다(F-3). 오프라인 기록 대결은 서버 대결이 없어 보내지 않는다(D-377). */
    private suspend fun report(run: BattleRun, call: suspend () -> Outcome<Unit>) {
        if (run.isOffline) return
        val reported = run.round.report(call)
        if (reported is Outcome.Failure) {
            _failure.value = reported.error
            guidanceOutput.play(Guidance.screenOnly(Guidance.Kind.REPORT_FAILED, GuidanceMessage.REPORT_FAILED))
        }
    }

    private fun progress(run: BattleRun, p: RunProgress) {
        if (!run.round.isMeasuring || !run.round.accept(p)) return
        run.feed.onProgress(run, p)
        updateMine(run) { p.toMine() }
        // 반올림한 표시 거리가 아니라 정확한 누적 거리로 도착을 본다(W54)
        if (run.feed.hasArrived(run, p.exactDistanceMeters)) finish(run, status = null)
    }

    private fun handle(run: BattleRun, m: ServerMessage) {
        when (m) {
            is BattleStart -> run.round.scope.launch { start(run, m) }

            is OpponentProgress -> onOpponentProgress(run, m)

            is BattleGuidance -> onBattleGuidance(run, m)

            is CountdownStarted -> onCountdownStarted(run, m)

            is BattleEnded -> if (m.battleId == run.battleId) finish(run, m.status)

            // 매칭 성사·실패와 친구 초대 끝 알림은 매칭 화면이 받는다(성사 뒤에야 이 조정자가 맡는다).
            is MatchFound, is MatchFailed, is FriendInviteDeclined, is FriendInviteExpired -> Unit

            is CrewRunStart, is CrewRunCancelled, is CrewRunRelay, is CrewRunEnded -> Unit

            is CrewRunChanged, is CrewRunDeparture -> Unit

            // 채팅은 같은 연결을 쓰는 ChatRoom이 받는다(D-103).
            is CrewChatPosted, is CrewChatRemoved, is CrewChatReactionChanged -> Unit
        }
    }

    /**
     * 상대 진행값. 상대 연결 확인 중(CHECKING)이면 격차가 낡은 값이라 근접 알림을 멈추고(onGapChanged(null)) 화면의 마지막 격차는
     * 그대로 둔다. 연결로 돌아오면 새 격차로 다시 시작한다(D-145 (1)).
     * 이 회가 측정을 시작하기 전에 온 값은 버린다: 측정 없이 진행 화면으로 넘어가 경과가 0에 멈추지 않게 한다(QA 1번).
     */
    private fun onOpponentProgress(run: BattleRun, m: OpponentProgress) {
        if (m.battleId != run.battleId || !run.round.isMeasuring) return
        if (m.connection == OpponentProgress.Connection.CHECKING) {
            runTracker.onGapChanged(null)
            updateRunning { it.copy(opponentConnection = m.connection) }
            return
        }
        runTracker.onGapChanged(m.gapMeters)
        updateRunning {
            it.copy(gapMeters = m.gapMeters, opponentDistance = m.opponentDistance, opponentConnection = m.connection)
        }
    }

    /** 서버 추월 안내. 측정 전에 온 안내는 상대 진행값과 같은 이유로 버린다. */
    private fun onBattleGuidance(run: BattleRun, m: BattleGuidance) {
        if (m.battleId != run.battleId || !run.round.isMeasuring) return
        announce(m.kind, m.gapMeters)
        updateRunning { it.copy(lastGuidance = m.kind) }
    }

    private fun onCountdownStarted(run: BattleRun, m: CountdownStarted) {
        if (m.battleId != run.battleId) return
        _state.update { current ->
            val mine = (current as? BattleRunState.Running)?.mine ?: MyProgress.ZERO
            BattleRunState.Closing(m.battleId, m.arrivedName, m.endsAt, mine)
        }
        run.round.scope.launch {
            val wait = Duration.between(clock.instant(), m.endsAt)
            if (!wait.isNegative) delay(wait.toMillis())
            finish(run, status = null)
        }
    }

    /**
     * 측정 중(Running) 상태만 갱신한다. 종료 카운트다운(Closing)·종료(Finished)는 진행 메시지로 덮지 않고(F-1), 측정 전 상태
     * (매칭·카운트다운)에서 진행 상태를 새로 만들지도 않는다(QA 1번). Running은 begin이 만든다.
     */
    private fun updateRunning(next: (BattleRunState.Running) -> BattleRunState.Running) {
        _state.update { current -> if (current is BattleRunState.Running) next(current) else current }
    }

    /** 내 측정값을 진행·종료 카운트다운 상태에 반영한다. 다른 상태(카운트다운·종료)는 그대로 둔다. */
    private fun updateMine(run: BattleRun, next: (MyProgress) -> MyProgress) {
        _state.update { current ->
            when (current) {
                is BattleRunState.Running -> if (current.battleId ==
                    run.battleId
                ) {
                    current.copy(mine = next(current.mine))
                } else {
                    current
                }

                is BattleRunState.Closing -> if (current.battleId ==
                    run.battleId
                ) {
                    current.copy(mine = next(current.mine))
                } else {
                    current
                }

                else -> current
            }
        }
    }

    /** 진행값 → 내 측정값. 경로선은 RunTracker의 지금 값을 옮긴다(D-261). */
    private fun RunProgress.toMine(): MyProgress =
        MyProgress(distance, elapsed, currentPace, averagePace, route = runTracker.routeLine.value)

    /**
     * 매칭 요청이 취소됐으면(이탈 취소가 요청을 끊음) null. 부른 코루틴 자신이 취소된 것이면 null로 삼키지 않고 취소를 잇는다(리뷰 N-9).
     */
    private suspend fun <T> Deferred<Outcome<T>>.awaitOrNull(): Outcome<T>? = try {
        await()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
    }

    /** 추월·역전 안내. 대결 음성·진동 설정은 RunTracker가 적용한다(F-2). */
    private fun announce(kind: BattleGuidance.Kind, gapMeters: Int) {
        when (kind) {
            BattleGuidance.Kind.OVERTOOK ->
                runTracker.onLeadChanged(Guidance.Kind.OVERTOOK, GuidanceMessage.OVERTOOK, listOf(gapMeters))

            BattleGuidance.Kind.OVERTAKEN ->
                runTracker.onLeadChanged(Guidance.Kind.OVERTAKEN, GuidanceMessage.OVERTAKEN)
        }
    }

    /** 측정 중이면 endRun(기록 저장) 뒤 이 회의 범위를 닫는다. */
    private fun finish(run: BattleRun, status: BattleEnded.Status?) {
        run.round.endMeasuring()
        close(run, status)
    }

    /** 측정 중이면 기록을 버린 뒤(RunTracker.discardRun) 이 회를 Discarded로 닫는다(D-266). */
    private fun discard(run: BattleRun) {
        run.round.discardMeasuring()
        close(run, BattleRunState.Discarded(run.battleId))
    }

    /** 이 회를 실패로 닫는다(연결 실패·시작 거부·범위 안 예외). */
    private fun fail(run: BattleRun, error: AppError) {
        _failure.value = error
        finish(run, status = null)
    }

    private fun close(run: BattleRun, status: BattleEnded.Status?) =
        close(run, BattleRunState.Finished(run.battleId, status))

    /** 이 회를 [end] 상태로 닫는다(채널 반납, 범위 취소). 이미 다른 회가 열렸으면 아무것도 하지 않는다. */
    private fun close(run: BattleRun, end: BattleRunState) {
        if (this.run !== run) return
        this.run = null
        run.channel?.close()
        _liveState.value = null
        _state.value = end
        run.round.cancel()
    }

    /** 이전 회가 남아 있으면 정리(측정 중이면 endRun, 채널 닫기, 범위 취소)하고 새 회를 연다. */
    private fun openRun(
        battleId: Long,
        distance: Distance,
        opponentName: String?,
        startDevice: RunStartSpec.StartDevice,
        isOffline: Boolean = false
    ): BattleRun {
        run?.let { previous ->
            run = null
            previous.round.endMeasuring()
            previous.channel?.close()
            previous.round.cancel()
        }
        _failure.value = null
        _liveState.value = null
        return BattleRun(battleId, distance, opponentName, startDevice, isOffline).also { run = it }
    }

    /**
     * 대결 한 회. 범위·측정 여부는 CoordinatedRound가, 예약·상대 종류 전이는 schedule이 맡는다(O-3).
     * 범위 안의 처리되지 않은 예외는 앱을 죽이지 않고 이 회를 연결 실패로 닫는다. [opponentName]은 진행 알림 "vs [상대]"에 싣는다(D-258).
     * [startDevice]는 이 회를 시작한 기기로 기록에 남는다(D-378). [isOffline]이면 서버 대결 없이 워치에서만 진행하는 기록 대결이다(D-377).
     */
    private inner class BattleRun(
        val battleId: Long,
        val distance: Distance,
        val opponentName: String?,
        val startDevice: RunStartSpec.StartDevice,
        val isOffline: Boolean
    ) {
        val round = CoordinatedRound(applicationScope, runTracker) { fail(this, AppError.Network) }

        @Volatile
        var channel: LiveChannel? = null
            private set

        var cardKind: CardKind = CardKind.BATTLE_RUN
            private set

        var feed: OpponentFeed = liveOpponentFeed
            private set

        /** 그만두기를 보냈는가(연타 막기). 이 회의 직렬 디스패처에서만 읽고 쓴다. */
        var isForfeiting = false

        /** 성사 뒤 GPS 예열(W55)과 그 상태. 이 회의 직렬 디스패처에서만 읽고 쓴다. */
        var warmUpJob: Job? = null
        var gps: GpsReadiness? = null

        /** 예열을 끈다. 측정이 시작되면 성사·카운트다운 상태가 사라지므로 상태는 지우지 않는다. */
        fun stopGpsWarmUp() {
            warmUpJob?.cancel()
            warmUpJob = null
        }

        /** 시작 예약을 받았는가. 같은 시작 신호(onStart·BattleStart 재전송)가 두 번 와도 한 번만 시작한다. */
        private var isScheduled = false

        fun attach(channel: LiveChannel) {
            this.channel = channel
        }

        /** 시작 예약. 이미 예약됐으면 거짓. track이 있으면 기록 상대 모드, cardKind가 있으면 기록 대결 카드 종류. */
        fun schedule(track: OpponentTrack?, cardKind: CardKind?): Boolean {
            if (isScheduled) return false
            isScheduled = true
            track?.let { feed = RecordedOpponentFeed(it, GapTracker(distance)) }
            cardKind?.let { this.cardKind = it }
            return true
        }
    }

    /** 상대 종류별 진행 처리(O-5). */
    private interface OpponentFeed {
        fun onProgress(run: BattleRun, p: RunProgress)

        /** 반올림 전 내 누적 거리(m)가 대결 거리에 닿았는가(W54). */
        fun hasArrived(run: BattleRun, mineMeters: Double): Boolean
    }

    /** 기록 상대: 상대 거리를 보간하고 10m 넘는 선두 변경을 추월 안내로 낸다(FR-188). 도착은 GapTracker가 판정한다(S-13). */
    private inner class RecordedOpponentFeed(private val track: OpponentTrack, private val gapTracker: GapTracker) :
        OpponentFeed {
        override fun onProgress(run: BattleRun, p: RunProgress) {
            val theirs = track.distanceAt(p.elapsed)
            val kind = gapTracker.update(p.distance, theirs)
            runTracker.onGapChanged(gapTracker.gapMeters)
            kind?.let { announce(it, gapTracker.gapMeters) }
            updateRunning { previous ->
                previous.copy(
                    gapMeters = gapTracker.gapMeters,
                    opponentDistance = theirs,
                    lastGuidance = kind ?: previous.lastGuidance
                )
            }
        }

        override fun hasArrived(run: BattleRun, mineMeters: Double): Boolean = gapTracker.hasArrived(mineMeters)
    }

    private companion object {
        /** 혼자 하는 기록 대결(셀프런·셀럽런). */
        val RECORD_BATTLE_KINDS = setOf(CardKind.SELF_RUN, CardKind.CELEB_RUN)

        val RECORD_COUNTDOWN: Duration = Duration.ofSeconds(3)

        /** GPS 예열 상한(5분, 워치 W53과 같다). 출발이 늦어져도 GPS를 계속 켜 두지 않는다. */
        val GPS_WARM_UP_LIMIT: Duration = Duration.ofMinutes(5)

        /** 오프라인 기록 대결의 상태 값 battleId 자리(서버 대결 없음, D-377 "RecordBattleStart(battleId = 0) 대신"). */
        const val OFFLINE_BATTLE_ID = 0L
        const val CANCEL_ATTEMPTS = 3
        const val CANCEL_RETRY_MILLIS = 1_000L

        /** 대기열 취소 거부 사유: 서버가 이미 짝을 지었다(FR-085). */
        const val ALREADY_MATCHED = "ALREADY_MATCHED"

        /** 랜덤 매칭으로 생기는 대결 종류(상대가 없으면 고스트). */
        val MATCHED_TYPES = setOf(BattleHistory.Type.RANDOM, BattleHistory.Type.GHOST)

        /** 끝낼 대결이 없다([findMatched]). 서버 대결 번호는 1부터다. */
        const val NO_BATTLE = -1L

        /** 남은 그만두기를 다시 보내는 기한. 넘으면 대결은 이미 끝났다고 보고 지운다(S15P31A207-278). */
        val PENDING_FORFEIT_LIFETIME: Duration = Duration.ofHours(1)
    }
}
