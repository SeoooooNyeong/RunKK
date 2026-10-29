package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
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
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.run.CoordinatedRound
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceMessage
import com.a207.runkk.app.domain.run.GuidanceOutput
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

/**
 * 대결 러닝의 측정과 실시간 메시지를 잇는다(6.1.14). 화면이 아니라 대결 한 회와 수명을 같이 한다:
 * onMatched·onRecordBattleStart 때 주입받은 applicationScope 아래에 자기 범위(SupervisorJob)를 만들고
 * endRun·forfeit 뒤 닫는다(5.1.2, design-review.md M44). 상태 변경은 모두 그 범위의 직렬 디스패처에서 돈다.
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
    private val applicationScope: CoroutineScope
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

    /** 이탈 뒤 대기열 취소(재시도 포함). 새 요청이 오면 멈춘다. */
    @Volatile
    private var abandonJob: Job? = null

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

            override fun hasArrived(run: BattleRun, mine: Distance): Boolean = mine.isAtLeast(run.distance)
        }

    /** 랜덤·고스트 매칭 성사. 매칭 화면·애니메이션은 ui.battle이 그린다(design-review.md M25). */
    fun onMatched(m: MatchFound) {
        val run = openRun(m.battleId, m.distance, m.opponentName)
        run.round.scope.launch {
            _state.value = BattleRunState.Matched(m)
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

    /** 나와의 싸움·유명인: 3초 카운트다운 뒤 측정을 시작하고 시작을 서버에 알린다(design-review.md M34). */
    fun onRecordBattleStart(s: RecordBattleStart) {
        val run = openRun(s.battleId, s.distance, s.opponentName)
        run.round.scope.launch {
            run.schedule(s.track, s.cardKind)
            _state.value = BattleRunState.Countdown(s.battleId, clock.instant().plus(RECORD_COUNTDOWN))
            delay(RECORD_COUNTDOWN.toMillis())
            if (begin(run)) report(run) { battleRepository.markStarted(s.battleId) }
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
     * 확인 뒤 그만두기(POST /battles/{id}/forfeit) → 기록 저장. 보고가 끝내 실패해도 기기 측정은 끝내고 failure로 알린다.
     */
    fun forfeit() {
        val run = run ?: return
        run.round.scope.launch {
            report(run) { battleRepository.forfeit(run.battleId) }
            finish(run, status = null)
        }
    }

    /**
     * 랜덤 매칭 요청(POST /battles/match, FR-080). applicationScope에서 보내 화면이 사라져도 응답을 받는다. 앞선 이탈의 취소
     * 재시도가 남아 있으면 먼저 멈춰 새 대기열 등록을 지우지 않게 한다.
     */
    suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance> {
        abandonJob?.cancelAndJoin()
        abandonJob = null
        val request = applicationScope.async { battleRepository.requestMatch(distance) }
        requestJob = request
        return request.await()
    }

    /**
     * 매칭 대기 중 화면이 사라질 때(시스템 제스처·탭 이동으로 ViewModel이 지워질 때) 서버 대기열에서 뺀다(FR-085).
     * 화면 범위는 이미 취소됐으므로 applicationScope에서 보낸다. 요청이 아직 서버에 가 있으면 끝난 뒤에 보내고(취소가 먼저 도착해
     * 대기열에 남지 않게), 요청이 받아들여지지 않았으면 보내지 않는다. 네트워크 실패는 1·2초 뒤 두 번 더 보내고, 거부(이미 성사·
     * 대기 없음)는 다시 보내지 않는다.
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
        }
    }

    /**
     * 이 프로세스가 [battleId] 대결을 맡고 있는가(열린 회가 있는가). 앱을 다시 켜 서버에만 남은 대결을 찾을 때 쓴다(배틀 QA 7번).
     */
    fun isRunning(battleId: Long): Boolean = run?.battleId == battleId

    /** 결과 화면이 Finished를 보여 준 뒤 부른다. 다음 대결 화면이 지난 회의 종료 상태를 받지 않게 Idle로 돌린다(S-17). */
    fun acknowledgeFinished() {
        _state.update { if (it is BattleRunState.Finished) BattleRunState.Idle else it }
        _failure.value = null
    }

    private suspend fun start(run: BattleRun, s: BattleStart) {
        if (run.battleId != s.battleId) return
        if (!run.schedule(s.opponentTrack?.let(OpponentTrack::from), cardKind = null)) return
        _state.value = BattleRunState.Countdown(s.battleId, s.startAt)
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
        val spec =
            RunStartSpec(
                mode = RunMode.BATTLE,
                cardKind = run.cardKind,
                battleId = run.battleId,
                todayPlan = PlanSnapshot(targetDistance = run.distance, targetPace = null),
                opponentName = run.opponentName
            )
        val started = run.round.begin(spec) { progress(run, it) }
        if (started is Outcome.Failure) {
            fail(run, started.error)
            return false
        }
        _state.value =
            BattleRunState.Running(run.battleId, gapMeters = null, opponentDistance = null, lastGuidance = null)
        return true
    }

    /** 서버 보고. 다시 보내도 실패하면 failure로 남기고 화면 안내를 낸다(F-3). */
    private suspend fun report(run: BattleRun, call: suspend () -> Outcome<Unit>) {
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
        if (run.feed.hasArrived(run, p.distance)) finish(run, status = null)
    }

    private fun handle(run: BattleRun, m: ServerMessage) {
        when (m) {
            is BattleStart -> run.round.scope.launch { start(run, m) }

            is OpponentProgress -> onOpponentProgress(run, m)

            is BattleGuidance -> onBattleGuidance(run, m)

            is CountdownStarted -> onCountdownStarted(run, m)

            is BattleEnded -> if (m.battleId == run.battleId) finish(run, m.status)

            // 매칭 성사·실패는 매칭 화면이 받는다(성사 뒤에야 이 조정자가 맡는다).
            is MatchFound, is MatchFailed -> Unit

            is CrewRunStart, is CrewRunCancelled, is CrewRunRelay, is CrewRunEnded -> Unit

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
    private suspend fun Deferred<Outcome<MatchAcceptance>>.awaitOrNull(): Outcome<MatchAcceptance>? = try {
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

    /** 이 회를 실패로 닫는다(연결 실패·시작 거부·범위 안 예외). */
    private fun fail(run: BattleRun, error: AppError) {
        _failure.value = error
        finish(run, status = null)
    }

    private fun close(run: BattleRun, status: BattleEnded.Status?) {
        if (this.run !== run) return
        this.run = null
        run.channel?.close()
        _liveState.value = null
        _state.value = BattleRunState.Finished(run.battleId, status)
        run.round.cancel()
    }

    /** 이전 회가 남아 있으면 정리(측정 중이면 endRun, 채널 닫기, 범위 취소)하고 새 회를 연다. */
    private fun openRun(battleId: Long, distance: Distance, opponentName: String): BattleRun {
        run?.let { previous ->
            run = null
            previous.round.endMeasuring()
            previous.channel?.close()
            previous.round.cancel()
        }
        _failure.value = null
        _liveState.value = null
        return BattleRun(battleId, distance, opponentName).also { run = it }
    }

    /**
     * 대결 한 회. 범위·측정 여부는 CoordinatedRound가, 예약·상대 종류 전이는 schedule이 맡는다(O-3).
     * 범위 안의 처리되지 않은 예외는 앱을 죽이지 않고 이 회를 연결 실패로 닫는다. [opponentName]은 진행 알림 "vs [상대]"에 싣는다(D-258).
     */
    private inner class BattleRun(val battleId: Long, val distance: Distance, val opponentName: String) {
        val round = CoordinatedRound(applicationScope, runTracker) { fail(this, AppError.Network) }

        @Volatile
        var channel: LiveChannel? = null
            private set

        var cardKind: CardKind = CardKind.BATTLE_RUN
            private set

        var feed: OpponentFeed = liveOpponentFeed
            private set

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

        fun hasArrived(run: BattleRun, mine: Distance): Boolean
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

        override fun hasArrived(run: BattleRun, mine: Distance): Boolean = gapTracker.hasArrived(mine)
    }

    private companion object {
        val RECORD_COUNTDOWN: Duration = Duration.ofSeconds(3)
        const val CANCEL_ATTEMPTS = 3
        const val CANCEL_RETRY_MILLIS = 1_000L
    }
}
