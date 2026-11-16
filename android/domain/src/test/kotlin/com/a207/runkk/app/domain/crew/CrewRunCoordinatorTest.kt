package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunDeparture
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.sample
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakeGuidanceOutput
import com.a207.runkk.app.domain.testing.FakeLiveChannelProvider
import com.a207.runkk.app.domain.testing.FakeLocationPermission
import com.a207.runkk.app.domain.testing.FakeMeasurementHost
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import com.a207.runkk.app.domain.testing.FakeUploadScheduler
import com.a207.runkk.app.domain.testing.FakeWatchGateway
import com.a207.runkk.app.domain.testing.T0
import com.a207.runkk.app.domain.testing.TestClock
import com.a207.runkk.app.domain.testing.runTracker
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** CrewRunCoordinator(6.1.14, 합산 방식): 대기 진입부터 크루런 한 회의 범위, 진행값 송신, 일시정지 보고, 끝내기. */
@OptIn(ExperimentalCoroutinesApi::class)
class CrewRunCoordinatorTest {
    private val log = CallLog()
    private val clock = TestClock()
    private val host = FakeMeasurementHost()
    private val live = FakeLiveChannelProvider()
    private val guidance = FakeGuidanceOutput()
    private val parentJob = SupervisorJob()

    /** quit이 돌려줄 실패(F-3). null이면 성공. */
    private var quitFailure: AppError? = null

    /** 마지막 quit의 거리(m)·경과. */
    private var quitArgs: Pair<Any?, Any?>? = null

    /** leaveLobby가 돌려줄 실패. null이면 성공. */
    private var leaveFailure: AppError? = null

    /** GET 대기실 응답(설계 05-32). 기본은 출발한 크루런의 진행 참가자 본인(memberId 7)이다. */
    private var lobbyAnswer: Outcome<Lobby> = Outcome.Success(lobby(LobbyPhase.RUNNING, isMeRunner = true))

    /** POST join 응답의 내 시작 절대시각. null이면 마지막 출발 사실의 startAt이다. */
    private var joinedAt: java.time.Instant? = null

    /** 마지막 출발 사실의 시작 시각(join 기본 응답). */
    private var lastStartAt: java.time.Instant = T0

    private var joinEnded = false
    private var joinFailure: AppError? = null

    /** 대기실·참여 호출(본 호출 기록 log와 따로 센다). */
    private val reads = mutableListOf<String>()

    private fun lobby(
        phase: LobbyPhase,
        isMeRunner: Boolean = false,
        canJoin: Boolean = false,
        candidates: List<Long> = emptyList(),
        startAt: java.time.Instant? = null
    ) = Lobby(
        11,
        listOf(Lobby.Participant("나", isReady = true, memberId = 7, isMe = true)),
        T0.plusSeconds(600),
        Distance(3_000),
        phase = phase,
        startAt = startAt,
        countdownParticipantIds = candidates,
        canJoin = canJoin,
        isMeRunner = isMeRunner,
        measurementRecordId = if (isMeRunner) "rec-11-7" else null
    )

    /** quit·leaveLobby만 쓰는 가짜 CrewRepository. 나머지 연산을 부르면 실패한다. */
    private val repository: CrewRepository =
        Proxy.newProxyInstance(CrewRepository::class.java.classLoader, arrayOf(CrewRepository::class.java)) {
                _,
                method,
                args
            ->
            when (method.name) {
                "quit" -> {
                    log.add("crew.quit:${args[0]}")
                    quitArgs = args[1] to args[2]
                    quitFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
                }

                "pause" -> {
                    log.add("crew.pause:${args[0]}:${(args[1] as Duration).seconds}")
                    Outcome.Success(Unit)
                }

                "lobby" -> {
                    reads.add("lobby")
                    lobbyAnswer
                }

                "join" -> {
                    reads.add("join")
                    joinFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(
                        CrewRunJoin(
                            11,
                            7,
                            1,
                            joinedAt ?: lastStartAt,
                            "rec-11-7",
                            isAlreadyJoined = false,
                            isEnded = joinEnded
                        )
                    )
                }

                "leaveLobby" -> {
                    log.add("crew.leaveLobby:${args[0]}")
                    leaveFailure?.let { Outcome.Failure(it) }
                        ?: Outcome.Success(Lobby(args[0] as Long, emptyList(), T0.plusSeconds(600), Distance(3_000)))
                }

                else -> error("unused: ${method.name}")
            }
        } as CrewRepository

    @AfterTest
    fun tearDown() {
        parentJob.cancel()
    }

    private fun TestScope.setUp(): Pair<RunTracker, CrewRunCoordinator> {
        val tracker =
            runTracker(
                FakePendingRunRepository(log),
                FakeUploadScheduler(log),
                FakeWatchGateway(),
                guidance,
                FakeLocationPermission(),
                host,
                FakeSettingsRepository(),
                clock,
                backgroundScope
            )
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler) + parentJob)
        return tracker to CrewRunCoordinator(tracker, live, repository, clock, appScope)
    }

    /** 출발 사실(옛 CrewRunStart)을 보낸다. 참여 응답의 시작 시각도 같은 값이다. */
    private suspend fun departAt(at: java.time.Instant) {
        lastStartAt = at
        live.channel.messages.emit(CrewRunStart(11, at, listOf("나", "너")))
    }

    private fun RunTracker.runTo(second: Long, meters: Double) {
        onLocationSample(sample(second, meters))
        clock.now = T0.plusSeconds(second)
        tick(clock.now)
    }

    @Test
    fun `대기실 나가기 - 서버에 알리고 채널을 닫은 뒤 Idle로 돌아간다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        var result: Outcome<Unit>? = null
        backgroundScope.launch { result = coordinator.leaveLobby(11) }
        runCurrent()
        assertEquals(Outcome.Success(Unit), result)
        assertEquals(listOf("crew.leaveLobby:11"), log.calls)
        assertTrue(live.channel.closed)
        assertEquals(CrewRunState.Idle, coordinator.state.value, "결과 화면으로 가지 않는다")
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `대기실 나가기 실패 - 채널과 대기 상태를 그대로 두고 실패를 돌려준다`() = runTest {
        leaveFailure = AppError.Network
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        var result: Outcome<Unit>? = null
        backgroundScope.launch { result = coordinator.leaveLobby(11) }
        runCurrent()
        assertEquals(Outcome.Failure(AppError.Network), result)
        assertEquals(false, live.channel.closed)
        assertEquals(CrewRunState.Lobby(11), coordinator.state.value)
    }

    @Test
    fun `대기실 나가기 - 출발 예약 뒤에는 서버에 보내지 않고 거부한다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        departAt(T0.plusSeconds(10))
        runCurrent()
        var result: Outcome<Unit>? = null
        backgroundScope.launch { result = coordinator.leaveLobby(11) }
        runCurrent()
        assertEquals(Outcome.Failure(AppError.Rejected("ALREADY_STARTED")), result)
        assertTrue(log.calls.none { it.startsWith("crew.leaveLobby") })
    }

    @Test
    fun `대기실 나가기 - 서버가 받는 사이 출발 예약이 오면 닫지 않고 ALREADY_STARTED`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        var result: Outcome<Unit>? = null
        departAt(T0.plusSeconds(10))
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { result = coordinator.leaveLobby(11) }
        runCurrent()
        assertEquals(Outcome.Failure(AppError.Rejected("ALREADY_STARTED")), result)
        assertEquals(false, live.channel.closed, "출발한 회의 채널을 닫지 않는다")
        assertIs<CrewRunState.Countdown>(coordinator.state.value)
    }

    @Test
    fun `대기실 나가기 - 부른 쪽 범위가 취소돼도 서버 알림과 정리를 끝낸다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        val caller = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { coordinator.leaveLobby(11) }
        caller.cancel()
        runCurrent()
        assertEquals(listOf("crew.leaveLobby:11"), log.calls)
        assertTrue(live.channel.closed)
        assertEquals(CrewRunState.Idle, coordinator.state.value)
    }

    @Test
    fun `TC-11043 출발 사실의 startAt(이미 +3초)부터 재고 앱이 3초를 더하지 않는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onLobbyEntered(postId = 11, distance = Distance(3_000))
        runCurrent()
        assertEquals(CrewRunState.Lobby(11), coordinator.state.value)
        departAt(T0.plusSeconds(10))
        runCurrent()
        assertEquals(CrewRunState.Countdown(11, T0.plusSeconds(10)), coordinator.state.value)
        advanceTimeBy(9_999)
        assertEquals(0, host.started)
        advanceTimeBy(2)
        assertEquals(1, host.started, "예약 + 3초(startAt)에 시작한다. 옛 조정자는 3초를 더 기다렸다")
        assertEquals(listOf("lobby", "join"), reads, "대기실을 다시 읽어 확인하고 참여 값으로 잰다")
        tracker.runTo(1, 0.0)
        tracker.runTo(2, 3.0)
        runCurrent()
        val report = live.channel.sent.last()
        assertEquals(ProgressReport.Channel.CREW_RUN, report.channel)
        assertEquals(11L, report.channelId)
    }

    @Test
    fun `측정 중 중계 메시지의 참가자 표와 합산 거리를 상태로 내보낸다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        departAt(T0)
        runCurrent()
        val entries =
            listOf(
                CrewRunRelay.Entry("너", Distance(900), CrewRunRelay.State.RUNNING),
                CrewRunRelay.Entry("나", Distance(800), CrewRunRelay.State.PAUSED, 7, isMe = true)
            )
        live.channel.messages.emit(CrewRunRelay(11, Distance(1_700), entries))
        runCurrent()
        val running = assertIs<CrewRunState.Running>(coordinator.state.value)
        assertEquals(entries, running.entries)
        assertEquals(Distance(1_700), running.totalDistance)
    }

    @Test
    fun `크루런 거리에 닿아도 측정을 끝내지 않고 내 측정값을 상태로 낸다(합산 방식)`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(1_000))
        runCurrent()
        departAt(T0)
        runCurrent()
        advanceTimeBy(3_001)
        (1L..400L).forEach { second -> tracker.runTo(second, (second - 1) * 3.0) }
        runCurrent()
        val running = assertIs<CrewRunState.Running>(coordinator.state.value)
        assertTrue(running.mine.distance.meters >= 1_000, "1km를 넘었다: ${running.mine.distance}")
        assertEquals(Duration.ofSeconds(400), running.mine.elapsed)
        assertTrue(running.mine.route.points.isNotEmpty())
        assertEquals(0, host.stopped, "목표 거리에 닿아도 측정은 그대로")
        assertTrue(guidance.played.any { it.kind == Guidance.Kind.SPLIT }, "자유런처럼 1km 구간 안내")
    }

    @Test
    fun `일시정지로 바뀌면 정지 때 경과를 서버에 한 번 알리고 재개는 진행값으로만 알린다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        departAt(T0)
        runCurrent()
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
        coordinator.pause()
        runCurrent()
        val paused = assertIs<CrewRunState.Running>(coordinator.state.value).mine
        assertTrue(paused.isPaused)
        coordinator.pause()
        runCurrent()
        assertEquals(
            listOf("crew.pause:11:${paused.elapsed.seconds}"),
            log.calls.filter { it.startsWith("crew.pause") }
        )
        val sentBefore = live.channel.sent.size
        coordinator.resume()
        clock.now = T0.plusSeconds(8)
        tracker.tick(clock.now)
        runCurrent()
        assertEquals(false, assertIs<CrewRunState.Running>(coordinator.state.value).mine.isPaused)
        assertTrue(live.channel.sent.size > sentBefore, "재개 뒤 진행값을 다시 보낸다")
        assertEquals(1, log.calls.count { it.startsWith("crew.pause") })
    }

    @Test
    fun `끝내기 - 마지막 거리·경과를 실은 quit 보고 뒤 endRun하고 범위를 닫는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        departAt(T0)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
        val mine = assertIs<CrewRunState.Running>(coordinator.state.value).mine
        coordinator.quit()
        runCurrent()
        assertEquals(listOf("crew.quit:11", "pending.save", "upload.requestUpload"), log.calls)
        assertEquals(mine.distance.meters to mine.elapsed, assertNotNull(quitArgs))
        assertIs<CrewRunState.Finished>(coordinator.state.value)
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `취소 메시지 - 범위를 닫고 사유를 남긴다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        live.channel.messages.emit(CrewRunCancelled(11, "참가자 부족"))
        runCurrent()
        assertEquals(CrewRunState.Finished(11, "참가자 부족"), coordinator.state.value)
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `F-1 출발 카운트다운 중 중계가 와도 Countdown을 지킨다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        departAt(T0.plusSeconds(10))
        runCurrent()
        live.channel.messages.emit(
            CrewRunRelay(11, Distance(0), listOf(CrewRunRelay.Entry("너", Distance(0), CrewRunRelay.State.RUNNING)))
        )
        runCurrent()
        assertEquals(CrewRunState.Countdown(11, T0.plusSeconds(10)), coordinator.state.value)
    }

    @Test
    fun `F-3 그만두기 보고가 실패하면 다시 보내고 그래도 실패하면 failure로 알린다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        departAt(T0)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
        quitFailure = AppError.Network
        coordinator.quit()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, log.calls.count { it == "crew.quit:11" })
        assertEquals(AppError.Network, coordinator.failure.value)
        assertIs<CrewRunState.Finished>(coordinator.state.value)
        assertEquals(1, host.stopped)
    }

    @Test
    fun `S-17 결과 화면이 확인하면 Idle로 돌아간다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        live.channel.messages.emit(CrewRunCancelled(11, "참가자 부족"))
        runCurrent()
        coordinator.acknowledgeFinished()
        assertEquals(CrewRunState.Idle, coordinator.state.value)
    }

    // ---------- 설계 05-32 출발·중간 참여 ----------

    @Test
    fun `TC-11024 카운트다운의 고정 후보면 Countdown, 준비를 풀어 후보에서 빠지면 대기실로 돌아간다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        coordinator.onLobby(lobby(LobbyPhase.COUNTDOWN, candidates = listOf(7), startAt = T0.plusSeconds(3)))
        runCurrent()
        assertEquals(CrewRunState.Countdown(11, T0.plusSeconds(3)), coordinator.state.value)
        coordinator.onLobby(lobby(LobbyPhase.COUNTDOWN, candidates = emptyList(), startAt = T0.plusSeconds(3)))
        runCurrent()
        assertEquals(CrewRunState.Lobby(11), coordinator.state.value, "후보가 아니면 자동 측정하지 않는다")
        assertEquals(0, host.started)
    }

    @Test
    fun `TC-11027 출발 사실을 받은 미준비 입장자는 자동 측정 없이 함께 뛰기·나가기를 고른다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        lobbyAnswer = Outcome.Success(lobby(LobbyPhase.RUNNING, canJoin = true, candidates = listOf(8)))
        live.channel.messages.emit(CrewRunDeparture(7, 11, 20, "11-18", T0, listOf(8)))
        runCurrent()
        assertEquals(CrewRunState.JoinChoice(11, departedCount = 1), coordinator.state.value)
        assertEquals(0, host.started)
        assertEquals(listOf("lobby"), reads, "참여는 고를 때만 보낸다")
        // 「나가기」: 서버에 알리지 않고 닫는다.
        backgroundScope.launch { coordinator.dismiss(11) }
        runCurrent()
        assertEquals(CrewRunState.Idle, coordinator.state.value)
        assertTrue(live.channel.closed)
        assertTrue(log.calls.none { it.startsWith("crew.leaveLobby") })
    }

    @Test
    fun `TC-11028 함께 뛰기는 승인 joinedAt부터 재고 서버 측정 기록 ID를 쓴다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        coordinator.onLobby(lobby(LobbyPhase.RUNNING, canJoin = true))
        runCurrent()
        assertEquals(CrewRunState.JoinChoice(11), coordinator.state.value)
        // 출발 5분 뒤 승인(응답이 1초 늦게 와도 승인 시각을 쓴다).
        clock.now = T0.plusSeconds(301)
        joinedAt = T0.plusSeconds(300)
        coordinator.join()
        runCurrent()
        assertEquals(1, host.started)
        tracker.runTo(311, 0.0)
        runCurrent()
        val running = assertIs<CrewRunState.Running>(coordinator.state.value)
        assertEquals(Duration.ofSeconds(11), running.mine.elapsed, "승인 시각(300초)부터 잰다. 출발 뒤 지난 시간을 더하지 않는다")
        tracker.runTo(320, 30.0)
        runCurrent()
        coordinator.quit()
        runCurrent()
        assertEquals(CrewRunState.Finished(11, null, "rec-11-7"), coordinator.state.value)
    }

    @Test
    fun `TC-11029 출발 사실이 두 번 오고 다시 읽어도 측정은 한 번이고 끝낸 참여는 다시 재지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        departAt(T0)
        live.channel.messages.emit(CrewRunDeparture(7, 11, 20, "11-18", T0, listOf(7)))
        runCurrent()
        coordinator.onLobby(lobby(LobbyPhase.RUNNING, isMeRunner = true))
        runCurrent()
        assertEquals(1, host.started)
        assertEquals(1, reads.count { it == "join" })
    }

    @Test
    fun `TC-11029 이미 끝낸 참가자는 대기실을 다시 열어도 측정을 다시 시작하지 않는다`() = runTest {
        joinEnded = true
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        coordinator.onLobby(lobby(LobbyPhase.RUNNING, isMeRunner = true))
        runCurrent()
        assertEquals(0, host.started)
        assertEquals(CrewRunState.Finished(11, null, null), coordinator.state.value)
    }

    @Test
    fun `TC-11027 출발 확인 읽기가 끝내 실패하면 출발 사실의 후보일 때만 그 시각부터 잰다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        coordinator.onLobby(lobby(LobbyPhase.COUNTDOWN, candidates = listOf(7), startAt = T0))
        runCurrent()
        lobbyAnswer = Outcome.Failure(AppError.Network)
        live.channel.messages.emit(CrewRunDeparture(7, 11, 20, "11-18", T0, listOf(7)))
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(3, reads.count { it == "lobby" })
        assertEquals(1, host.started)
    }

    @Test
    fun `출발 뒤 참여하지 않은 대기실은 중계를 받아도 진행 화면이 되지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        live.channel.messages.emit(
            CrewRunRelay(11, Distance(10), listOf(CrewRunRelay.Entry("너", Distance(10), CrewRunRelay.State.RUNNING)))
        )
        runCurrent()
        assertEquals(CrewRunState.Lobby(11), coordinator.state.value)
    }

    @Test
    fun `같은 크루런 대기실에 다시 들어와도 진행 중인 회를 닫지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        departAt(T0)
        runCurrent()
        assertIs<CrewRunState.Running>(coordinator.state.value)
        coordinator.onLobbyEntered(11, Distance(3_000))
        runCurrent()
        assertIs<CrewRunState.Running>(coordinator.state.value)
        assertEquals(0, host.stopped)
        assertEquals(1, live.opens)
    }
}
