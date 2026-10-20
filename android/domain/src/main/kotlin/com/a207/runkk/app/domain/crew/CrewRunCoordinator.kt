package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.run.CrewRank
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
 * 크루런 러닝의 측정과 중계 메시지를 잇는다(6.1.14). 크루런 대기 진입(onLobbyEntered) 때 applicationScope 아래에
 * 자기 범위를 만들고 endRun·quit·취소 뒤 닫는다(5.1.2). 상태 변경은 그 범위의 직렬 디스패처에서만 돈다.
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

    @Volatile
    private var run: CrewRun? = null

    private class CrewRun(val scope: CoroutineScope, val postId: Long, val distance: Distance, val myName: String) {
        var channel: LiveChannel? = null

        /** 시작 예약을 받았는가. 같은 CrewRunStart가 두 번 와도 한 번만 시작한다. */
        var scheduled = false
        var running = false
    }

    /** 크루런 대기실 진입(ui.crew가 CrewRepository.enterLobby 성공 뒤 부른다). 채널을 열고 중계를 받는다. */
    fun onLobbyEntered(postId: Long, distance: Distance, myName: String) {
        val run = openRun(postId, distance, myName)
        run.scope.launch {
            _state.value = CrewRunState.Lobby(postId)
            val channel = liveChannelProvider.open()
            run.channel = channel
            channel.connect()
            channel.messages.collect { handle(run, it) }
        }
    }

    /** 모집 일시 + 3초 카운트다운 뒤 측정 시작(FR-148). */
    fun onStart(s: CrewRunStart) {
        val run = run ?: return
        run.scope.launch { start(run, s) }
    }

    fun onProgress(p: RunProgress) {
        val run = run ?: return
        run.scope.launch { progress(run, p) }
    }

    fun onMessage(m: ServerMessage) {
        val run = run ?: return
        run.scope.launch { handle(run, m) }
    }

    fun onArrived() {
        val run = run ?: return
        run.scope.launch { finish(run, cancelledReason = null) }
    }

    /** 확인 뒤 중간 그만두기(POST /crew-runs/{id}/quit, design-review.md E4) → 기록 저장. */
    fun quit() {
        val run = run ?: return
        run.scope.launch {
            crewRepository.quit(run.postId)
            finish(run, cancelledReason = null)
        }
    }

    private suspend fun start(run: CrewRun, s: CrewRunStart) {
        if (s.postId != run.postId || run.scheduled) return
        run.scheduled = true
        val startAt = s.startAt.plus(START_COUNTDOWN)
        _state.value = CrewRunState.Countdown(run.postId, startAt)
        val wait = Duration.between(clock.instant(), startAt)
        if (!wait.isNegative) delay(wait.toMillis())
        val spec = RunStartSpec(mode = RunMode.CREW_RUN, cardKind = CardKind.CREW_RUN, crewRunPostId = run.postId)
        if (runTracker.startRun(spec) is Outcome.Failure) {
            close(run, cancelledReason = null)
            return
        }
        run.running = true
        run.scope.launch { runTracker.progress.filter { it.elapsed > Duration.ZERO }.collect { progress(run, it) } }
    }

    private fun progress(run: CrewRun, p: RunProgress) {
        if (!run.running) return
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
            is CrewRunStart -> run.scope.launch { start(run, m) }

            is CrewRunRelay -> {
                if (m.postId != run.postId) return
                _state.value = CrewRunState.Running(run.postId, m.entries)
                val mine = m.entries.firstOrNull { it.name == run.myName }?.rank
                if (mine != null &&
                    mine in 1..m.entries.size
                ) {
                    runTracker.onRankChanged(CrewRank(mine, m.entries.size))
                }
            }

            is CrewRunCancelled -> if (m.postId == run.postId) finish(run, m.reason)

            is CrewRunEnded -> if (m.postId == run.postId) finish(run, cancelledReason = null)

            else -> Unit
        }
    }

    private fun finish(run: CrewRun, cancelledReason: String?) {
        if (run.running) {
            run.running = false
            runTracker.endRun()
        }
        close(run, cancelledReason)
    }

    private fun close(run: CrewRun, cancelledReason: String?) {
        if (this.run !== run) return
        this.run = null
        run.channel?.close()
        _state.value = CrewRunState.Finished(run.postId, cancelledReason)
        run.scope.cancel()
    }

    /**
     * 이전 회가 남아 있으면 정리(측정 중이면 endRun, 채널 닫기, 범위 취소)하고 새 범위를 만든다.
     * 범위 안의 예외(채널 연결 실패 등)는 앱을 죽이지 않고 그 회를 닫는다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun openRun(postId: Long, distance: Distance, myName: String): CrewRun {
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
        lateinit var created: CrewRun
        val onFailure = CoroutineExceptionHandler { _, _ -> finish(created, cancelledReason = null) }
        val scope = CoroutineScope(parent + SupervisorJob(parent.job) + dispatcher + onFailure)
        created = CrewRun(scope, postId, distance, myName)
        return created.also { run = it }
    }

    private companion object {
        val START_COUNTDOWN: Duration = Duration.ofSeconds(3)
    }
}
