package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
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
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * 대결 러닝의 측정과 실시간 메시지를 잇는다(6.1.14). 화면이 아니라 대결 한 회와 수명을 같이 한다:
 * onMatched·onRecordBattleStart 때 주입받은 applicationScope 아래에 자기 범위(SupervisorJob)를 만들고
 * endRun·forfeit 뒤 닫는다(5.1.2, design-review.md M44). 상태 변경은 모두 그 범위의 직렬 디스패처에서 돈다.
 *
 * 랜덤 대결은 1초마다 진행값을 채널로 보내고 서버의 OpponentProgress·BattleGuidance를 반영한다.
 * 기록 상대(고스트·나와의 싸움·유명인)는 OpponentTrack으로 상대 거리를 보간하고 GapTracker로 추월·도착을 판정한다(D-05).
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

    @Volatile
    private var run: BattleRun? = null

    /** 대결 한 회의 상태. run 범위의 직렬 디스패처에서만 바꾼다. */
    private class BattleRun(val scope: CoroutineScope) {
        var battleId: Long? = null
        var distance: Distance? = null
        var cardKind: CardKind = CardKind.BATTLE_RUN
        var channel: LiveChannel? = null
        var track: OpponentTrack? = null
        var gapTracker: GapTracker? = null

        /** 시작 예약을 받았는가. 같은 시작 신호(onStart·BattleStart 재전송)가 두 번 와도 한 번만 시작한다. */
        var scheduled = false
        var running = false
    }

    /** 랜덤·고스트 매칭 성사. 매칭 화면·애니메이션은 ui.battle이 그린다(design-review.md M25). */
    fun onMatched(m: MatchFound) {
        val run = openRun()
        run.scope.launch {
            run.battleId = m.battleId
            run.distance = m.distance
            _state.value = BattleRunState.Matched(m)
            val channel = liveChannelProvider.open()
            run.channel = channel
            channel.connect()
            channel.messages.collect { handle(run, it) }
        }
    }

    /** 랜덤·고스트 시작 예약: startAt에 측정 시작. opponentTrack이 있으면 기록 상대 모드. */
    fun onStart(s: BattleStart) {
        val run = run ?: return
        run.scope.launch { start(run, s) }
    }

    /** 나와의 싸움·유명인: 3초 카운트다운 뒤 측정을 시작하고 시작을 서버에 알린다(design-review.md M34). */
    fun onRecordBattleStart(s: RecordBattleStart) {
        val run = openRun()
        run.scope.launch {
            run.battleId = s.battleId
            run.distance = s.distance
            run.cardKind = s.cardKind
            run.track = s.track
            run.gapTracker = GapTracker(s.distance)
            run.scheduled = true
            _state.value = BattleRunState.Countdown(s.battleId, clock.instant().plus(RECORD_COUNTDOWN))
            delay(RECORD_COUNTDOWN.toMillis())
            if (begin(run)) battleRepository.markStarted(s.battleId)
        }
    }

    fun onProgress(p: RunProgress) {
        val run = run ?: return
        run.scope.launch { progress(run, p) }
    }

    fun onMessage(m: ServerMessage) {
        val run = run ?: return
        run.scope.launch { handle(run, m) }
    }

    /** 본인 도착 → 기록 저장. */
    fun onArrived() {
        val run = run ?: return
        run.scope.launch { finish(run, status = null) }
    }

    /** 확인 뒤 그만두기(POST /battles/{id}/forfeit) → 기록 저장. */
    fun forfeit() {
        val run = run ?: return
        run.scope.launch {
            run.battleId?.let { battleRepository.forfeit(it) }
            finish(run, status = null)
        }
    }

    private suspend fun start(run: BattleRun, s: BattleStart) {
        if (run.battleId != s.battleId || run.scheduled) return
        run.scheduled = true
        s.opponentTrack?.let { table ->
            run.track = OpponentTrack.from(table)
            run.gapTracker = run.distance?.let(::GapTracker)
        }
        if (s.opponentTrack != null) run.cardKind = CardKind.BATTLE_RUN
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
        if (run.running) return false
        val battleId = run.battleId ?: return false
        val spec =
            RunStartSpec(
                mode = RunMode.BATTLE,
                cardKind = run.cardKind,
                battleId = battleId,
                todayPlan = run.distance?.let { PlanSnapshot(targetDistance = it, targetPace = null) }
            )
        if (runTracker.startRun(spec) is Outcome.Failure) {
            close(run, status = null)
            return false
        }
        run.running = true
        _state.value =
            BattleRunState.Running(battleId, gapMeters = null, opponentDistance = null, lastGuidance = null)
        run.scope.launch { runTracker.progress.filter { it.elapsed > Duration.ZERO }.collect { progress(run, it) } }
        return true
    }

    private fun progress(run: BattleRun, p: RunProgress) {
        val battleId = run.battleId
        if (!run.running || battleId == null) return
        val track = run.track
        val gapTracker = run.gapTracker
        if (track != null && gapTracker != null) {
            val theirs = track.distanceAt(p.elapsed)
            val kind = gapTracker.update(p.distance, theirs)
            runTracker.onGapChanged(gapTracker.gapMeters)
            kind?.let { guidanceOutput.play(it.toGuidance(gapTracker.gapMeters)) }
            val previous = (_state.value as? BattleRunState.Running)?.lastGuidance
            _state.value = BattleRunState.Running(battleId, gapTracker.gapMeters, theirs, kind ?: previous)
        } else {
            run.channel?.send(
                ProgressReport(
                    channel = ProgressReport.Channel.BATTLE,
                    channelId = battleId,
                    distanceMeters = p.distance.meters,
                    elapsedSeconds = p.elapsed.seconds,
                    sentAt = clock.instant()
                )
            )
        }
        val target = run.distance
        if (target != null && p.distance.isAtLeast(target)) finish(run, status = null)
    }

    private fun handle(run: BattleRun, m: ServerMessage) {
        when (m) {
            is BattleStart -> run.scope.launch { start(run, m) }

            is OpponentProgress -> {
                if (m.battleId != run.battleId) return
                runTracker.onGapChanged(m.gapMeters)
                val previous = (_state.value as? BattleRunState.Running)?.lastGuidance
                _state.value = BattleRunState.Running(m.battleId, m.gapMeters, m.opponentDistance, previous)
            }

            is BattleGuidance -> {
                if (m.battleId != run.battleId) return
                guidanceOutput.play(m.kind.toGuidance(m.gapMeters))
                val current = _state.value as? BattleRunState.Running
                _state.value =
                    current?.copy(lastGuidance = m.kind)
                        ?: BattleRunState.Running(m.battleId, m.gapMeters, null, m.kind)
            }

            is CountdownStarted -> {
                if (m.battleId != run.battleId) return
                _state.value = BattleRunState.Closing(m.battleId, m.arrivedName, m.endsAt)
                run.scope.launch {
                    val wait = Duration.between(clock.instant(), m.endsAt)
                    if (!wait.isNegative) delay(wait.toMillis())
                    finish(run, status = null)
                }
            }

            is BattleEnded -> if (m.battleId == run.battleId) finish(run, m.status)

            else -> Unit
        }
    }

    /** 측정 중이면 endRun(기록 저장) 뒤 이 회의 범위를 닫는다. */
    private fun finish(run: BattleRun, status: BattleEnded.Status?) {
        if (run.running) {
            run.running = false
            runTracker.endRun()
        }
        close(run, status)
    }

    private fun close(run: BattleRun, status: BattleEnded.Status?) {
        if (this.run !== run) return
        this.run = null
        run.channel?.close()
        run.battleId?.let { _state.value = BattleRunState.Finished(it, status) }
        run.scope.cancel()
    }

    /**
     * 이전 회가 남아 있으면 정리(측정 중이면 endRun, 채널 닫기, 범위 취소)하고 새 범위를 만든다.
     * 직렬 디스패처로 한 회의 상태 변경이 겹치지 않는다. 범위 안의 예외(채널 연결 실패 등)는 앱을 죽이지 않고
     * 그 회를 닫는다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun openRun(): BattleRun {
        run?.let { previous ->
            run = null
            if (previous.running) {
                previous.running = false
                runTracker.endRun()
            }
            previous.channel?.close()
            previous.scope.cancel()
        }
        val parent = applicationScope.coroutineContext
        val base = parent[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default
        val dispatcher = base.limitedParallelism(1)
        lateinit var created: BattleRun
        val onFailure = CoroutineExceptionHandler { _, _ -> finish(created, status = null) }
        created = BattleRun(CoroutineScope(parent + SupervisorJob(parent.job) + dispatcher + onFailure))
        return created.also { run = it }
    }

    private fun BattleGuidance.Kind.toGuidance(gapMeters: Int): Guidance = when (this) {
        BattleGuidance.Kind.OVERTOOK ->
            Guidance(Guidance.Kind.OVERTOOK, "상대를 추월했어요! ${gapMeters}m 앞서는 중", voice = false, vibration = true)

        BattleGuidance.Kind.OVERTAKEN ->
            Guidance(Guidance.Kind.OVERTAKEN, "역전당했어요", voice = false, vibration = true)
    }

    private companion object {
        val RECORD_COUNTDOWN: Duration = Duration.ofSeconds(3)
    }
}
