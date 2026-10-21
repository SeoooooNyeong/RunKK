package com.a207.runkk.app.domain.crew

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
import com.a207.runkk.app.domain.run.CrewRank
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루런 러닝의 측정과 중계 메시지를 잇는다(6.1.14). 크루런 대기 진입(onLobbyEntered) 때 applicationScope 아래에
 * 자기 범위를 만들고 endRun·quit·취소 뒤 닫는다(5.1.2). 상태 변경은 그 범위의 직렬 디스패처에서만 돈다.
 * 출발 카운트다운(Countdown) 중에 온 중계는 그 상태를 덮지 않는다(F-1). 그만두기 보고를 다시 보내도 실패하거나
 * 채널에 연결하지 못하면 failure로 알린다(F-3, O-6).
 */
@Singleton
class CrewRunCoordinator
@Inject
constructor(
    private val runTracker: RunTracker,
    private val liveChannelProvider: LiveChannelProvider,
    private val crewRepository: CrewRepository,
    private val clock: Clock,
    private val applicationScope: CoroutineScope
) {
    private val _state = MutableStateFlow<CrewRunState>(CrewRunState.Idle)
    val state: StateFlow<CrewRunState> = _state.asStateFlow()

    private val _failure = MutableStateFlow<AppError?>(null)

    /** 이 회에서 생긴 실패(그만두기 보고 실패, 채널 연결 실패, 측정 시작 거부). 새 회·acknowledgeFinished 때 지운다. */
    val failure: StateFlow<AppError?> = _failure.asStateFlow()

    @Volatile
    private var run: CrewRun? = null

    /** 크루런 대기실 진입(ui.crew가 CrewRepository.enterLobby 성공 뒤 부른다). 채널을 열고 중계를 받는다. */
    fun onLobbyEntered(postId: Long, distance: Distance, myName: String) {
        val run = openRun(postId, distance, myName)
        run.round.scope.launch {
            _state.value = CrewRunState.Lobby(postId)
            val channel = liveChannelProvider.open()
            run.attach(channel)
            // 연결 직후 오는 메시지(BattleStart 등)를 잃지 않게 연결 전에 구독한다.
            run.round.scope.launch(start = CoroutineStart.UNDISPATCHED) { channel.messages.collect { handle(run, it) } }
            val connected = channel.connect()
            if (connected is Outcome.Failure) fail(run, connected.error)
        }
    }

    /** 모집 일시 + 3초 카운트다운 뒤 측정 시작(FR-148). */
    fun onStart(s: CrewRunStart) {
        val run = run ?: return
        run.round.scope.launch { start(run, s) }
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

    fun onArrived() {
        val run = run ?: return
        run.round.scope.launch { finish(run, cancelledReason = null) }
    }

    /** 확인 뒤 중간 그만두기(POST /crew-runs/{id}/quit, design-review.md E4) → 기록 저장. 보고가 끝내 실패하면 failure로 알린다. */
    fun quit() {
        val run = run ?: return
        run.round.scope.launch {
            val reported = run.round.report { crewRepository.quit(run.postId) }
            if (reported is Outcome.Failure) _failure.value = reported.error
            finish(run, cancelledReason = null)
        }
    }

    /**
     * 대기실만 나가기(출발 전, FR-147). 서버에 나가기를 알리고(CrewRepository.leaveLobby) 성공하면 채널을 닫고 이 회를 정리해
     * Idle로 돌린다(종료가 아니므로 Finished를 내지 않는다). 부른 화면의 범위가 취소돼도 알림과 정리는 applicationScope에서 끝낸다.
     * 출발 예약(CrewRunStart)을 받은 뒤에는 서버에 보내지 않고 ALREADY_STARTED로 거부한다(그만두기는 quit). 실패하면 채널과
     * 대기 상태를 그대로 두고 실패를 돌려준다.
     */
    suspend fun leaveLobby(postId: Long): Outcome<Unit> {
        val current = run?.takeIf { it.postId == postId }
        if (current?.isScheduled == true) return Outcome.Failure(AppError.Rejected(ALREADY_STARTED))
        return applicationScope
            .async {
                when (val left = crewRepository.leaveLobby(postId)) {
                    is Outcome.Success -> {
                        current?.let(::closeQuietly)
                        Outcome.Success(Unit)
                    }

                    is Outcome.Failure -> left
                }
            }.await()
    }

    /** 결과 화면이 Finished를 보여 준 뒤 부른다. 다음 크루런 화면이 지난 회의 종료 상태를 받지 않게 Idle로 돌린다(S-17). */
    fun acknowledgeFinished() {
        _state.update { if (it is CrewRunState.Finished) CrewRunState.Idle else it }
        _failure.value = null
    }

    private suspend fun start(run: CrewRun, s: CrewRunStart) {
        if (s.postId != run.postId || !run.schedule()) return
        val startAt = s.startAt.plus(START_COUNTDOWN)
        _state.value = CrewRunState.Countdown(run.postId, startAt)
        val wait = Duration.between(clock.instant(), startAt)
        if (!wait.isNegative) delay(wait.toMillis())
        val spec = RunStartSpec(mode = RunMode.CREW_RUN, cardKind = CardKind.CREW_RUN, crewRunPostId = run.postId)
        val started = run.round.begin(spec) { progress(run, it) }
        if (started is Outcome.Failure) {
            fail(run, started.error)
            return
        }
        _state.value = CrewRunState.Running(run.postId, run.lastEntries)
    }

    private fun progress(run: CrewRun, p: RunProgress) {
        if (!run.round.isMeasuring || !run.round.accept(p)) return
        run.channel?.send(
            ProgressReport(
                channel = ProgressReport.Channel.CREW_RUN,
                channelId = run.postId,
                distanceMeters = p.distance.meters,
                elapsedSeconds = p.elapsed.seconds,
                sentAt = clock.instant()
            )
        )
        if (p.distance.isAtLeast(run.distance)) finish(run, cancelledReason = null)
    }

    private fun handle(run: CrewRun, m: ServerMessage) {
        when (m) {
            is CrewRunStart -> run.round.scope.launch { start(run, m) }

            is CrewRunRelay -> onRelay(run, m)

            is CrewRunCancelled -> if (m.postId == run.postId) finish(run, m.reason)

            is CrewRunEnded -> if (m.postId == run.postId) finish(run, cancelledReason = null)

            // 대결 메시지는 이 조정자와 무관하다. 메시지 종류가 늘면 컴파일러가 여기를 알린다(S-10).
            is MatchFound, is BattleStart, is OpponentProgress -> Unit

            is BattleGuidance, is CountdownStarted, is BattleEnded -> Unit
        }
    }

    /** 중계를 상태로 내보내고 본인 순위를 안내 입력으로 넘긴다. 출발 카운트다운·종료 상태는 덮지 않는다(F-1). */
    private fun onRelay(run: CrewRun, m: CrewRunRelay) {
        if (m.postId != run.postId) return
        run.remember(m.entries)
        _state.update { current ->
            when (current) {
                is CrewRunState.Countdown, is CrewRunState.Finished -> current
                else -> CrewRunState.Running(run.postId, m.entries)
            }
        }
        val mine = m.entries.firstOrNull { it.name == run.myName }?.rank
        if (mine != null && mine in 1..m.entries.size) runTracker.onRankChanged(CrewRank(mine, m.entries.size))
    }

    private fun finish(run: CrewRun, cancelledReason: String?) {
        run.round.endMeasuring()
        close(run, cancelledReason)
    }

    private fun fail(run: CrewRun, error: AppError) {
        _failure.value = error
        finish(run, cancelledReason = null)
    }

    private fun close(run: CrewRun, cancelledReason: String?) {
        if (this.run !== run) return
        this.run = null
        run.channel?.close()
        _state.value = CrewRunState.Finished(run.postId, cancelledReason)
        run.round.cancel()
    }

    /** 측정 전 회를 결과 없이 닫는다(대기실 나가기). 채널을 닫고 범위를 취소한 뒤 Idle로 돌린다. */
    private fun closeQuietly(run: CrewRun) {
        if (this.run !== run || run.isScheduled) return
        this.run = null
        run.channel?.close()
        _state.update { if (it is CrewRunState.Lobby && it.postId == run.postId) CrewRunState.Idle else it }
        run.round.cancel()
    }

    /** 이전 회가 남아 있으면 정리(측정 중이면 endRun, 채널 닫기, 범위 취소)하고 새 회를 연다. */
    private fun openRun(postId: Long, distance: Distance, myName: String): CrewRun {
        run?.let { previous ->
            run = null
            previous.round.endMeasuring()
            previous.channel?.close()
            previous.round.cancel()
        }
        _failure.value = null
        return CrewRun(postId, distance, myName).also { run = it }
    }

    /** 크루런 한 회. 범위·측정 여부는 CoordinatedRound가 맡는다(S-2, O-3). 범위 안 예외는 연결 실패로 닫는다. */
    private inner class CrewRun(val postId: Long, val distance: Distance, val myName: String) {
        val round = CoordinatedRound(applicationScope, runTracker) { fail(this, AppError.Network) }

        @Volatile
        var channel: LiveChannel? = null
            private set

        /** 마지막 중계 표. 측정이 시작되면 Running 상태의 첫 표로 쓴다. */
        var lastEntries: List<CrewRunRelay.Entry> = emptyList()
            private set

        /** 시작 예약을 받았는가. 같은 CrewRunStart가 두 번 와도 한 번만 시작한다. */
        @Volatile
        var isScheduled = false
            private set

        fun attach(channel: LiveChannel) {
            this.channel = channel
        }

        fun remember(entries: List<CrewRunRelay.Entry>) {
            lastEntries = entries
        }

        /** 시작 예약. 이미 예약됐으면 거짓. */
        fun schedule(): Boolean {
            if (isScheduled) return false
            isScheduled = true
            return true
        }
    }

    private companion object {
        val START_COUNTDOWN: Duration = Duration.ofSeconds(3)

        /** 서버 common.ReasonCode.ALREADY_STARTED와 같은 이름. */
        const val ALREADY_STARTED = "ALREADY_STARTED"
    }
}
