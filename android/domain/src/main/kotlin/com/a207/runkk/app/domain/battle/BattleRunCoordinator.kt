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
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.run.CoordinatedRound
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
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

    @Volatile
    private var run: BattleRun? = null

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
        val run = openRun(m.battleId, m.distance)
        run.round.scope.launch {
            _state.value = BattleRunState.Matched(m)
            val channel = liveChannelProvider.open()
            run.attach(channel)
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
        val run = openRun(s.battleId, s.distance)
        run.round.scope.launch {
            run.schedule(s.track, s.cardKind)
            _state.value = BattleRunState.Countdown(s.battleId, clock.instant().plus(RECORD_COUNTDOWN))
            delay(RECORD_COUNTDOWN.toMillis())
            if (begin(run)) report(run) { battleRepository.markStarted(s.battleId) }
        }
    }

    /** 화면이 진행값을 넘길 때. 측정 구독과 같은 값은 한 번만 처리한다(S-16). */
    fun onProgress(p: RunProgress) {
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

    /** 확인 뒤 그만두기(POST /battles/{id}/forfeit) → 기록 저장. 보고가 끝내 실패해도 기기 측정은 끝내고 failure로 알린다. */
    fun forfeit() {
        val run = run ?: return
        run.round.scope.launch {
            report(run) { battleRepository.forfeit(run.battleId) }
            finish(run, status = null)
        }
    }

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
                todayPlan = PlanSnapshot(targetDistance = run.distance, targetPace = null)
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
            guidanceOutput.play(Guidance.screenOnly(Guidance.Kind.REPORT_FAILED, "대결 상태를 서버에 알리지 못했어요"))
        }
    }

    private fun progress(run: BattleRun, p: RunProgress) {
        if (!run.round.isMeasuring || !run.round.accept(p)) return
        run.feed.onProgress(run, p)
        if (run.feed.hasArrived(run, p.distance)) finish(run, status = null)
    }

    private fun handle(run: BattleRun, m: ServerMessage) {
        when (m) {
            is BattleStart -> run.round.scope.launch { start(run, m) }
            is OpponentProgress -> onOpponentProgress(run, m)
            is BattleGuidance -> onBattleGuidance(run, m)
            is CountdownStarted -> onCountdownStarted(run, m)
            is BattleEnded -> if (m.battleId == run.battleId) finish(run, m.status)
            is MatchFound, is CrewRunStart, is CrewRunCancelled, is CrewRunRelay, is CrewRunEnded -> Unit
        }
    }

    private fun onOpponentProgress(run: BattleRun, m: OpponentProgress) {
        if (m.battleId != run.battleId) return
        runTracker.onGapChanged(m.gapMeters)
        updateRunning { previous ->
            BattleRunState.Running(m.battleId, m.gapMeters, m.opponentDistance, previous?.lastGuidance)
        }
    }

    private fun onBattleGuidance(run: BattleRun, m: BattleGuidance) {
        if (m.battleId != run.battleId) return
        announce(m.kind, m.gapMeters)
        updateRunning { previous ->
            previous?.copy(lastGuidance = m.kind) ?: BattleRunState.Running(m.battleId, m.gapMeters, null, m.kind)
        }
    }

    private fun onCountdownStarted(run: BattleRun, m: CountdownStarted) {
        if (m.battleId != run.battleId) return
        _state.value = BattleRunState.Closing(m.battleId, m.arrivedName, m.endsAt)
        run.round.scope.launch {
            val wait = Duration.between(clock.instant(), m.endsAt)
            if (!wait.isNegative) delay(wait.toMillis())
            finish(run, status = null)
        }
    }

    /** 측정 중 상태만 갱신한다. 종료 카운트다운(Closing)·종료(Finished)는 진행 메시지로 덮지 않는다(F-1). */
    private fun updateRunning(next: (BattleRunState.Running?) -> BattleRunState.Running) {
        _state.update { current ->
            when (current) {
                is BattleRunState.Closing, is BattleRunState.Finished -> current
                else -> next(current as? BattleRunState.Running)
            }
        }
    }

    /** 추월·역전 안내. 대결 음성·진동 설정은 RunTracker가 적용한다(F-2). */
    private fun announce(kind: BattleGuidance.Kind, gapMeters: Int) {
        when (kind) {
            BattleGuidance.Kind.OVERTOOK ->
                runTracker.onLeadChanged(Guidance.Kind.OVERTOOK, "상대를 추월했어요! ${gapMeters}m 앞서는 중")

            BattleGuidance.Kind.OVERTAKEN -> runTracker.onLeadChanged(Guidance.Kind.OVERTAKEN, "역전당했어요")
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
        _state.value = BattleRunState.Finished(run.battleId, status)
        run.round.cancel()
    }

    /** 이전 회가 남아 있으면 정리(측정 중이면 endRun, 채널 닫기, 범위 취소)하고 새 회를 연다. */
    private fun openRun(battleId: Long, distance: Distance): BattleRun {
        run?.let { previous ->
            run = null
            previous.round.endMeasuring()
            previous.channel?.close()
            previous.round.cancel()
        }
        _failure.value = null
        return BattleRun(battleId, distance).also { run = it }
    }

    /**
     * 대결 한 회. 범위·측정 여부는 CoordinatedRound가, 예약·상대 종류 전이는 schedule이 맡는다(O-3).
     * 범위 안의 처리되지 않은 예외는 앱을 죽이지 않고 이 회를 연결 실패로 닫는다.
     */
    private inner class BattleRun(val battleId: Long, val distance: Distance) {
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
                BattleRunState.Running(run.battleId, gapTracker.gapMeters, theirs, kind ?: previous?.lastGuidance)
            }
        }

        override fun hasArrived(run: BattleRun, mine: Distance): Boolean = gapTracker.hasArrived(mine)
    }

    private companion object {
        val RECORD_COUNTDOWN: Duration = Duration.ofSeconds(3)
    }
}
