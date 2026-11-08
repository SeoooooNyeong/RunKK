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
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.TrackPoint
import com.a207.runkk.app.domain.live.TrackTable
import com.a207.runkk.app.domain.run.CoordinatedRound
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.ProgressNotice
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.sample
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakeGpsWarmUp
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
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class FakeBattleRepository(private val log: CallLog) : BattleRepository {
    /** markStarted·forfeit가 돌려줄 실패(F-3). null이면 성공. */
    var reportFailure: AppError? = null

    /** requestMatch를 붙잡아 두는 문(요청이 서버에 가 있는 동안 이탈하는 경우). */
    var requestGate: CompletableDeferred<Unit>? = null

    /** cancel이 돌려줄 실패. null이면 성공. */
    var cancelFailure: AppError? = null

    override suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance> {
        log.add("battle.requestMatch:${distance.meters}")
        requestGate?.await()
        return Outcome.Success(MatchAcceptance(isWaiting = true, distance = distance))
    }

    override suspend fun cancel(): Outcome<MatchAcceptance> {
        log.add("battle.cancel")
        return cancelFailure?.let { Outcome.Failure(it) }
            ?: Outcome.Success(MatchAcceptance(isWaiting = false, distance = null))
    }

    override suspend fun selfCandidates(): Outcome<RecordBattleCandidates> = error("unused")

    override suspend fun celebrityCandidates(distance: Distance?): Outcome<RecordBattleCandidates> = error("unused")

    override suspend fun recordTracks(kind: RecordBattleKind): Outcome<List<RecordTrack>> = error("unused")

    override suspend fun celebrityTrack(celebrityId: Long): Outcome<RecordTrack> = error("unused")

    override suspend fun startSelfBattle(distance: Distance): Outcome<RecordBattleStart> = error("unused")

    override suspend fun startCelebrityBattle(celebrityId: Long): Outcome<RecordBattleStart> = error("unused")

    override suspend fun markStarted(battleId: Long): Outcome<Unit> {
        log.add("battle.markStarted:$battleId")
        return reportFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override suspend fun forfeit(battleId: Long): Outcome<Unit> {
        log.add("battle.forfeit:$battleId")
        return reportFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override suspend fun cancel(battleId: Long): Outcome<Unit> {
        log.add("battle.cancel:$battleId")
        return reportFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    /** active가 차례로 돌려줄 결과(비면 진행 중인 대결 없음). */
    val activeResults = ArrayDeque<Outcome<ActiveBattle?>>()

    override suspend fun active(): Outcome<ActiveBattle?> {
        log.add("battle.active")
        return activeResults.removeFirstOrNull() ?: Outcome.Success(null)
    }

    override suspend fun result(battleId: Long): Outcome<BattleResult> = error("unused")

    override suspend fun history(filter: BattleHistory.Type?, cursor: Long?): Outcome<BattleHistory> = error("unused")

    override suspend fun matchStatus(): Outcome<MatchAcceptance> = error("unused")

    /** cancelFriendInvite가 차례로 돌려줄 실패(비면 성공). */
    val cancelInviteFailures = ArrayDeque<AppError>()

    /** createFriendInvite를 붙잡아 두는 문(만드는 중에 화면을 떠나는 경우). */
    var createGate: CompletableDeferred<Unit>? = null

    override suspend fun createFriendInvite(distance: Distance): Outcome<FriendInvite> {
        log.add("battle.createInvite:${distance.meters}")
        createGate?.await()
        return Outcome.Success(
            FriendInvite(41, "tok", "https://k15a207.p.ssafy.io/invite/battle/tok", distance, T0.plusSeconds(300))
        )
    }

    override suspend fun myFriendInvite(): Outcome<FriendInvite?> = error("unused")

    override suspend fun cancelFriendInvite(inviteId: Long): Outcome<Unit> {
        log.add("battle.cancelInvite:$inviteId")
        return cancelInviteFailures.removeFirstOrNull()?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override suspend fun friendInvite(token: String): Outcome<FriendInviteView> = error("unused")

    override suspend fun acceptFriendInvite(token: String): Outcome<Long> = error("unused")

    override suspend fun declineFriendInvite(token: String): Outcome<Unit> = error("unused")
}

/** BattleRunCoordinator(6.1.14): 기록 대결 시작 보고, TrackTable → OpponentTrack, 범위 닫힘. */
@OptIn(ExperimentalCoroutinesApi::class)
class BattleRunCoordinatorTest {
    private val log = CallLog()
    private val clock = TestClock()
    private val host = FakeMeasurementHost()
    private val output = FakeGuidanceOutput()
    private val pending = FakePendingRunRepository(log)
    private val live = FakeLiveChannelProvider()
    private val repository = FakeBattleRepository(log)
    private val forfeits = InMemoryPendingForfeitStore()
    private val settings = FakeSettingsRepository()
    private val watch = FakeWatchGateway()
    private val gps = FakeGpsWarmUp()
    private val parentJob = SupervisorJob()

    @AfterTest
    fun tearDown() {
        parentJob.cancel()
    }

    private fun TestScope.setUp(): Pair<RunTracker, BattleRunCoordinator> {
        val tracker =
            runTracker(
                pending,
                FakeUploadScheduler(log),
                watch,
                output,
                FakeLocationPermission(),
                host,
                settings,
                clock,
                backgroundScope
            )
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler) + parentJob)
        return tracker to BattleRunCoordinator(tracker, live, repository, output, clock, appScope, gps, forfeits)
    }

    private fun RunTracker.runTo(second: Long, meters: Double) {
        onLocationSample(sample(second, meters))
        clock.now = T0.plusSeconds(second)
        tick(clock.now)
    }

    private val selfStart =
        RecordBattleStart(
            battleId = 7,
            opponentName = "10/02의 나",
            distance = Distance(100),
            track = OpponentTrack.uniform(Distance(100), Duration.ofSeconds(50)),
            cardKind = CardKind.SELF_RUN
        )

    @Test
    fun `기록 대결 시작 - 3초 카운트다운 뒤 측정을 시작하고 markStarted를 보낸다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        runCurrent()
        assertIs<BattleRunState.Countdown>(coordinator.state.value)
        assertEquals(0, host.started)
        advanceTimeBy(3_001)
        assertEquals(1, host.started)
        assertEquals(listOf("battle.markStarted:7"), log.calls)
        assertIs<BattleRunState.Running>(coordinator.state.value)
    }

    @Test
    fun `기록 상대 - OpponentTrack으로 상대 거리와 격차를 계산하고 실시간 채널로 보내지 않는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(10, 30.0)
        runCurrent()
        // 10초에 나 30m, 상대(50초 100m 균등) 20m → +10m
        val running = assertIs<BattleRunState.Running>(coordinator.state.value)
        assertEquals(Distance(20), running.opponentDistance)
        assertEquals(10, running.gapMeters)
        assertTrue(live.channel.sent.isEmpty())
    }

    @Test
    fun `기록 상대 - 10m 넘게 앞서다 뒤처지면 역전 안내를 낸다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(10, 33.0)
        runCurrent()
        tracker.runTo(30, 40.0)
        runCurrent()
        assertEquals(Guidance.Kind.OVERTAKEN, output.played.last().kind)
    }

    @Test
    fun `랜덤 대결 - BattleStart의 시작 시각에 측정을 시작하고 진행값을 채널로 보낸다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0.plusSeconds(13), opponentTrack = null))
        runCurrent()
        assertEquals(0, host.started)
        advanceTimeBy(13_001)
        assertEquals(1, host.started)
        assertEquals(emptyList(), log.calls)
        tracker.runTo(1, 0.0)
        tracker.runTo(2, 3.0)
        runCurrent()
        val report = live.channel.sent.last()
        assertEquals(ProgressReport.Channel.BATTLE, report.channel)
        assertEquals(3L, report.channelId)
        assertEquals(3L, report.distanceMeters)
    }

    @Test
    fun `랜덤 대결 - 상대 진행값의 격차와 서버 추월 안내를 반영한다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = null))
        runCurrent()
        live.channel.messages.emit(
            OpponentProgress(
                3,
                "상대",
                Distance(500),
                Duration.ofSeconds(150),
                -20,
                OpponentProgress.Connection.CONNECTED
            )
        )
        live.channel.messages.emit(BattleGuidance(3, BattleGuidance.Kind.OVERTAKEN, -20))
        runCurrent()
        assertEquals(Guidance.Kind.OVERTAKEN, output.played.last().kind)
        assertEquals(-20, assertIs<BattleRunState.Running>(coordinator.state.value).gapMeters)
    }

    @Test
    fun `QA-1 측정 시작 전에 온 상대 진행값과 추월 안내는 진행 화면으로 넘기지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        val connected = OpponentProgress.Connection.CONNECTED
        live.channel.messages.emit(OpponentProgress(3, "상대", Distance(10), Duration.ofSeconds(5), -10, connected))
        live.channel.messages.emit(BattleGuidance(3, BattleGuidance.Kind.OVERTAKEN, -10))
        runCurrent()
        assertEquals(BattleRunState.Matched(match), coordinator.state.value)
        assertTrue(output.played.isEmpty())
        assertTrue(watch.proximity.isEmpty())

        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0.plusSeconds(10), opponentTrack = null))
        runCurrent()
        live.channel.messages.emit(OpponentProgress(3, "상대", Distance(20), Duration.ofSeconds(6), -20, connected))
        runCurrent()
        assertIs<BattleRunState.Countdown>(coordinator.state.value)

        advanceTimeBy(10_001)
        live.channel.messages.emit(OpponentProgress(3, "상대", Distance(30), Duration.ofSeconds(7), -30, connected))
        runCurrent()
        assertEquals(-30, assertIs<BattleRunState.Running>(coordinator.state.value).gapMeters)
    }

    @Test
    fun `QA-7 이 프로세스가 맡은 대결인지 알려 준다 - 닫힌 회는 맡지 않은 것으로 본다`() = runTest {
        val (_, coordinator) = setUp()
        assertFalse(coordinator.isRunning(3))
        coordinator.onMatched(match)
        runCurrent()
        assertTrue(coordinator.isRunning(3))
        assertFalse(coordinator.isRunning(4))
        live.channel.messages.emit(BattleEnded(3, BattleEnded.Status.FINISHED))
        runCurrent()
        assertFalse(coordinator.isRunning(3))
    }

    @Test
    fun `QA-5 랜덤 대결의 실시간 연결 상태를 알리고 회가 닫히면 지운다`() = runTest {
        val (_, coordinator) = setUp()
        assertNull(coordinator.liveState.value)
        coordinator.onMatched(match)
        runCurrent()
        assertEquals(LiveState.CONNECTED, coordinator.liveState.value)
        live.channel.state.value = LiveState.CONNECTING
        runCurrent()
        assertEquals(LiveState.CONNECTING, coordinator.liveState.value)
        live.channel.messages.emit(BattleEnded(3, BattleEnded.Status.FINISHED))
        runCurrent()
        assertNull(coordinator.liveState.value)
    }

    @Test
    fun `고스트 대결 - BattleStart의 opponentTrack이 있으면 기록 상대 모드로 보간한다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        val table = TrackTable(listOf(TrackPoint(10, 20), TrackPoint(20, 40)))
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = table))
        runCurrent()
        tracker.runTo(1, 0.0)
        tracker.runTo(10, 30.0)
        runCurrent()
        assertEquals(Distance(20), assertIs<BattleRunState.Running>(coordinator.state.value).opponentDistance)
        assertTrue(live.channel.sent.isEmpty())
    }

    @Test
    fun `같은 시작 신호가 다시 와도 진행 중인 대결을 닫지 않는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        val start = BattleStart(battleId = 3, startAt = T0, opponentTrack = null)
        live.channel.messages.emit(start)
        runCurrent()
        coordinator.onStart(start)
        live.channel.messages.emit(start)
        runCurrent()
        assertEquals(1, host.started)
        assertIs<BattleRunState.Running>(coordinator.state.value)
        tracker.runTo(1, 0.0)
        tracker.runTo(2, 3.0)
        runCurrent()
        assertEquals(3L, live.channel.sent.last().distanceMeters)
    }

    @Test
    fun `채널 연결이 실패하면 앱을 죽이지 않고 그 회를 닫는다`() = runTest {
        val (_, coordinator) = setUp()
        live.channel.connectFailure = AppError.Network
        coordinator.onMatched(match)
        runCurrent()
        assertIs<BattleRunState.Finished>(coordinator.state.value)
        assertEquals(AppError.Network, coordinator.failure.value, "O-6: 연결 실패를 결과 대기와 구분한다")
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `새 회를 열면 측정 중이던 이전 회를 endRun하고 채널을 닫는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
        coordinator.onMatched(match)
        runCurrent()
        assertEquals(1, host.stopped)
        assertEquals(BattleRunState.Matched(match), coordinator.state.value)
    }

    @Test
    fun `TC-9634 랜덤 대결 그만두기 - forfeit 보고 뒤 endRun(기록 업로드)하고 Finished로 닫는다`() = runTest {
        val (tracker, coordinator) = setUp()
        startRandom(tracker, coordinator)
        coordinator.forfeit()
        runCurrent()
        assertEquals(listOf("battle.forfeit:3", "pending.save", "upload.requestUpload"), log.calls)
        assertEquals(1, host.stopped)
        assertIs<BattleRunState.Finished>(coordinator.state.value)
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `TC-9632 D119 셀프런 그만두기 - forfeit 보고 뒤 패배로 기록을 남기고 Finished로 닫는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
        coordinator.forfeit()
        runCurrent()
        assertEquals(
            listOf("battle.markStarted:7", "battle.forfeit:7", "pending.save", "upload.requestUpload"),
            log.calls
        )
        assertEquals(1, host.stopped)
        assertIs<BattleRunState.Finished>(coordinator.state.value)
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `D119 셀프런 취소 - cancel 보고 뒤 기록을 버리고 Discarded로 닫는다(업로드 0)`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
        coordinator.cancel()
        runCurrent()
        assertEquals(listOf("battle.markStarted:7", "battle.cancel:7"), log.calls)
        assertEquals(1, host.stopped)
        assertEquals(BattleRunState.Discarded(7), coordinator.state.value)
        assertFalse(tracker.isMeasuring)
        assertNull(tracker.takeUnclaimedEnd())
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `TC-9633 D119 셀럽런 취소도 기록을 버리고 Discarded로 닫는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart.copy(battleId = 8, cardKind = CardKind.CELEB_RUN))
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        runCurrent()
        coordinator.cancel()
        runCurrent()
        assertEquals(listOf("battle.markStarted:8", "battle.cancel:8"), log.calls)
        assertEquals(BattleRunState.Discarded(8), coordinator.state.value)
    }

    @Test
    fun `TC-9638 셀프런 카운트다운 중 다시 고르기 - 측정 없이 cancel만 보내고 Discarded로 닫는다(D119)`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        runCurrent()
        coordinator.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals(listOf("battle.cancel:7"), log.calls)
        assertEquals(0, host.started)
        assertEquals(BattleRunState.Discarded(7), coordinator.state.value)
    }

    @Test
    fun `TC-9635 discardMeasuring은 두 번 불러도 한 번만 버린다`() = runTest {
        val (tracker, _) = setUp()
        val round = CoordinatedRound(CoroutineScope(StandardTestDispatcher(testScheduler) + parentJob), tracker) {}
        round.begin(RunStartSpec(mode = RunMode.BATTLE, cardKind = CardKind.SELF_RUN, battleId = 1)) {}
        round.discardMeasuring()
        round.discardMeasuring()
        assertEquals(1, host.stopped)
        assertFalse(round.isMeasuring)
        assertFalse(tracker.isMeasuring)
        assertTrue(log.calls.isEmpty())
        round.cancel()
    }

    @Test
    fun `S-17 기록을 버린 회도 결과 화면 없이 확인하면 Idle로 돌아간다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        runCurrent()
        coordinator.forfeit()
        runCurrent()
        coordinator.acknowledgeFinished()
        assertEquals(BattleRunState.Idle, coordinator.state.value)
    }

    @Test
    fun `isBusy - 열린 회가 있거나 자유런 측정 중이면 참이다(D-273)`() = runTest {
        val (tracker, coordinator) = setUp()
        assertFalse(coordinator.isBusy())
        coordinator.onRecordBattleStart(selfStart)
        runCurrent()
        assertTrue(coordinator.isBusy())
        coordinator.forfeit()
        runCurrent()
        assertFalse(coordinator.isBusy())
        tracker.startRun(RunStartSpec(mode = RunMode.FREE, cardKind = CardKind.FREE_RUN))
        assertTrue(coordinator.isBusy())
    }

    @Test
    fun `TC-9669 초대 취소 - 일시 오류면 1초·2초 뒤 두 번 더 보내고 거부는 다시 보내지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        repository.cancelInviteFailures += listOf(AppError.Network, AppError.Network)
        coordinator.cancelInvite(9)
        runCurrent()
        assertEquals(listOf("battle.cancelInvite:9"), log.calls)
        advanceTimeBy(1_001)
        assertEquals(2, log.calls.size)
        advanceTimeBy(2_001)
        assertEquals(List(3) { "battle.cancelInvite:9" }, log.calls)
        assertTrue(parentJob.children.none())

        log.calls.clear()
        repository.cancelInviteFailures += AppError.Rejected("PERMISSION_DENIED")
        coordinator.cancelInvite(10)
        advanceTimeBy(5_000)
        assertEquals(listOf("battle.cancelInvite:10"), log.calls)
    }

    @Test
    fun `초대 취소 결과를 기다릴 수 있고 INVITE_ALREADY_JOINED는 거부로 그대로 알린다`() = runTest {
        val (_, coordinator) = setUp()
        repository.cancelInviteFailures += AppError.Rejected("INVITE_ALREADY_JOINED")
        val outcome = coordinator.cancelInvite(11).await()
        assertEquals(Outcome.Failure(AppError.Rejected("INVITE_ALREADY_JOINED")), outcome)
        assertEquals(listOf("battle.cancelInvite:11"), log.calls)
    }

    @Test
    fun `초대를 만드는 중에 화면을 떠나면 만들어진 뒤에 그 초대를 취소한다(D-271)`() = runTest {
        val (_, coordinator) = setUp()
        repository.createGate = CompletableDeferred()
        val screen = backgroundScope.launch { coordinator.createInvite(Distance(5_000)) }
        runCurrent()
        screen.cancel()
        coordinator.abandonInvite()
        runCurrent()
        assertEquals(listOf("battle.createInvite:5000"), log.calls, "만들기 전에는 취소할 초대가 없다")
        repository.createGate?.complete(Unit)
        runCurrent()
        assertEquals(listOf("battle.createInvite:5000", "battle.cancelInvite:41"), log.calls)
    }

    @Test
    fun `만든 초대를 떠나면 취소하고 떠날 초대가 없으면 아무것도 하지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.abandonInvite()
        runCurrent()
        assertTrue(log.calls.isEmpty())
        assertIs<Outcome.Success<FriendInvite>>(coordinator.createInvite(Distance(3_000)))
        coordinator.abandonInvite()
        coordinator.abandonInvite()
        runCurrent()
        assertEquals(1, log.calls.count { it == "battle.cancelInvite:41" }, "한 번만 취소한다")
    }

    @Test
    fun `AN8-16 성사되거나 만료·거절로 끝난 초대는 떠나도 다시 취소하지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        assertIs<Outcome.Success<FriendInvite>>(coordinator.createInvite(Distance(3_000)))
        coordinator.onMatched(match)
        coordinator.abandonInvite()
        runCurrent()
        assertEquals(0, log.calls.count { it.startsWith("battle.cancelInvite") }, "성사된 초대")

        assertIs<Outcome.Success<FriendInvite>>(coordinator.createInvite(Distance(3_000)))
        coordinator.forgetInvite()
        coordinator.abandonInvite()
        runCurrent()
        assertEquals(0, log.calls.count { it.startsWith("battle.cancelInvite") }, "만료·거절된 초대")
    }

    @Test
    fun `그만두기를 두 번 눌러도 보고는 한 번이다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        runCurrent()
        coordinator.forfeit()
        coordinator.forfeit()
        runCurrent()
        assertEquals(1, log.calls.count { it == "battle.forfeit:7" })
    }

    /** 랜덤 대결을 측정 중까지 연다. [startDevice]가 null이면 폰 화면처럼 기기를 넘기지 않는다(기본값). */
    private suspend fun TestScope.startRandom(
        tracker: RunTracker,
        coordinator: BattleRunCoordinator,
        startDevice: RunStartSpec.StartDevice? = null
    ) {
        if (startDevice == null) coordinator.onMatched(match) else coordinator.onMatched(match, startDevice)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0.plusSeconds(13), opponentTrack = null))
        runCurrent()
        advanceTimeBy(13_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
    }

    @Test
    fun `본인 도착 - endRun하고 범위를 닫는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(30, 101.0)
        runCurrent()
        assertIs<BattleRunState.Finished>(coordinator.state.value)
        assertEquals(1, host.stopped)
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `W55 랜덤 대결 성사 뒤 GPS를 예열하고 상태를 성사·카운트다운에 실은 뒤 측정이 시작되면 끈다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        assertEquals(1, gps.collectors)
        assertEquals(30.0, gps.maxAccuracyMeters)
        assertEquals(BattleRunState.Matched(match), coordinator.state.value)
        gps.emit(GpsReadiness.SEARCHING)
        runCurrent()
        assertEquals(BattleRunState.Matched(match, GpsReadiness.SEARCHING), coordinator.state.value)
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0.plusSeconds(13), opponentTrack = null))
        runCurrent()
        assertEquals(BattleRunState.Countdown(3, T0.plusSeconds(13), GpsReadiness.SEARCHING), coordinator.state.value)
        gps.emit(GpsReadiness.READY)
        runCurrent()
        // 한 번 준비되면 정확도가 잠깐 나빠져도 준비됨을 지킨다
        gps.emit(GpsReadiness.SEARCHING)
        runCurrent()
        assertEquals(BattleRunState.Countdown(3, T0.plusSeconds(13), GpsReadiness.READY), coordinator.state.value)
        advanceTimeBy(13_001)
        assertEquals(1, host.started)
        assertEquals(0, gps.collectors)
        assertIs<BattleRunState.Running>(coordinator.state.value)
        // 예열 위치는 측정 기록에 들어가지 않는다
        assertEquals(RunProgress.INITIAL.distance, tracker.progress.value.distance)
    }

    @Test
    fun `W55 출발 전에 그만두면 예열을 끄고 기록 대결은 예열하지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        gps.emit(GpsReadiness.SEARCHING)
        runCurrent()
        assertEquals(1, gps.collectors)
        coordinator.forfeit()
        runCurrent()
        assertEquals(0, gps.collectors)
        assertIs<BattleRunState.Finished>(coordinator.state.value)

        coordinator.acknowledgeFinished()
        coordinator.onRecordBattleStart(selfStart)
        runCurrent()
        assertEquals(0, gps.collectors)
        assertEquals(null, (coordinator.state.value as BattleRunState.Countdown).gps)
    }

    @Test
    fun `W55 예열 상한 시간이 지나면 끄고 상태를 지운다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        gps.emit(GpsReadiness.READY)
        runCurrent()
        assertEquals(BattleRunState.Matched(match, GpsReadiness.READY), coordinator.state.value)
        advanceTimeBy(Duration.ofMinutes(5).toMillis() + 1)
        assertEquals(0, gps.collectors)
        assertEquals(BattleRunState.Matched(match), coordinator.state.value)
    }

    @Test
    fun `W54 랜덤 대결 도착은 반올림 전 거리로 본다 - 999_7m는 표시가 1000m여도 도착이 아니다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = null))
        runCurrent()
        tracker.runTo(1, 0.0)
        tracker.runTo(300, 999.7)
        runCurrent()
        assertEquals(1_000L, live.channel.sent.last().distanceMeters)
        assertIs<BattleRunState.Running>(coordinator.state.value)
        assertEquals(0, host.stopped)
        tracker.runTo(301, 1_000.2)
        runCurrent()
        assertIs<BattleRunState.Finished>(coordinator.state.value)
        assertEquals(1, host.stopped)
    }

    @Test
    fun `W54 기록 대결 도착도 반올림 전 거리로 본다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(30, 99.7)
        runCurrent()
        assertIs<BattleRunState.Running>(coordinator.state.value)
        tracker.runTo(31, 100.1)
        runCurrent()
        assertIs<BattleRunState.Finished>(coordinator.state.value)
    }

    @Test
    fun `대결 종료 메시지 - endRun 뒤 범위를 닫고 채널을 닫는다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = null))
        runCurrent()
        live.channel.messages.emit(BattleEnded(3, BattleEnded.Status.OPPONENT_FORFEITED))
        runCurrent()
        assertEquals(
            BattleRunState.Finished(3, BattleEnded.Status.OPPONENT_FORFEITED),
            coordinator.state.value
        )
        assertTrue(live.channel.closed)
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `매칭 - 상태를 MATCHED로 바꾸고 채널을 연결한다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        assertEquals(BattleRunState.Matched(match), coordinator.state.value)
        assertEquals(1, live.channel.connects)
    }

    @Test
    fun `F-1 종료 카운트다운 중 상대 진행값·추월 안내가 와도 Closing을 지킨다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = null))
        runCurrent()
        live.channel.messages.emit(CountdownStarted(3, "상대", T0.plusSeconds(30)))
        live.channel.messages.emit(
            OpponentProgress(
                3,
                "상대",
                Distance(1_000),
                Duration.ofSeconds(300),
                -40,
                OpponentProgress.Connection.CONNECTED
            )
        )
        live.channel.messages.emit(BattleGuidance(3, BattleGuidance.Kind.OVERTAKEN, -40))
        runCurrent()
        assertEquals(BattleRunState.Closing(3, "상대", T0.plusSeconds(30)), coordinator.state.value)
    }

    @Test
    fun `F-1 기록 상대 진행값도 종료 카운트다운을 덮지 않는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        val table = TrackTable(listOf(TrackPoint(10, 20), TrackPoint(400, 1_000)))
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = table))
        runCurrent()
        live.channel.messages.emit(CountdownStarted(3, "고스트", T0.plusSeconds(60)))
        runCurrent()
        tracker.runTo(1, 0.0)
        tracker.runTo(10, 30.0)
        runCurrent()
        assertIs<BattleRunState.Closing>(coordinator.state.value)
    }

    @Test
    fun `F-2 추월·역전 안내는 화면에 보이고 진동은 대결 진동 설정을 따르며 음성은 없다`() = runTest {
        settings.snapshot =
            SettingsSnapshot.SAFE.copy(
                isLocationConsented = true,
                guidanceSettings = GuidanceSettings.DEFAULT.copy(isBattleVoiceOn = true, isBattleVibrationOn = false)
            )
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = null))
        runCurrent()
        live.channel.messages.emit(BattleGuidance(3, BattleGuidance.Kind.OVERTOOK, 12))
        runCurrent()
        val played = output.played.last()
        assertEquals(Guidance.Kind.OVERTOOK, played.kind)
        assertEquals(false, played.hasVoice, "추월·역전은 음성을 쓰지 않는다(FR-109, TF-AN-03)")
        assertEquals(false, played.hasVibration, "진동 끔")
        assertEquals(listOf<Any>(12), played.args)
    }

    @Test
    fun `F-3 그만두기 보고가 실패하면 다시 보내고 그래도 실패하면 failure로 알린다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()
        repository.reportFailure = AppError.Network
        coordinator.forfeit()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, log.calls.count { it == "battle.forfeit:7" })
        assertEquals(AppError.Network, coordinator.failure.value)
        assertEquals(1, host.stopped, "기기 측정은 사용자의 그만두기대로 끝낸다")
        assertIs<BattleRunState.Finished>(coordinator.state.value, "셀프런 그만두기는 보고가 실패해도 기록을 남긴다(D119)")
    }

    @Test
    fun `F-3 시작 보고가 실패해도 측정은 계속하고 failure로 알린다`() = runTest {
        repository.reportFailure = AppError.Rejected("INVALID_STATE")
        val (_, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(1, log.calls.count { it == "battle.markStarted:7" }, "거부(Rejected)는 다시 보내지 않는다")
        assertEquals(AppError.Rejected("INVALID_STATE"), coordinator.failure.value)
        assertIs<BattleRunState.Running>(coordinator.state.value)
    }

    @Test
    fun `S-16 화면이 onProgress를 불러도 진행값을 두 번 보내지 않는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = null))
        runCurrent()
        tracker.runTo(1, 0.0)
        tracker.runTo(2, 3.0)
        runCurrent()
        val sent = live.channel.sent.size
        coordinator.onProgress(tracker.progress.value)
        runCurrent()
        assertEquals(sent, live.channel.sent.size)
    }

    @Test
    fun `S-17 결과 화면이 확인하면 다음 대결을 위해 Idle로 돌아간다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleEnded(3, BattleEnded.Status.FINISHED))
        runCurrent()
        assertIs<BattleRunState.Finished>(coordinator.state.value)
        coordinator.acknowledgeFinished()
        assertEquals(BattleRunState.Idle, coordinator.state.value)
        assertEquals(null, coordinator.failure.value)
    }

    @Test
    fun `진행 상태에 내 거리·경과·페이스를 담는다 - 화면이 측정 모듈을 보지 않게`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(10, 30.0)
        runCurrent()
        val running = assertIs<BattleRunState.Running>(coordinator.state.value)
        assertEquals(tracker.progress.value.distance, running.mine.distance)
        assertEquals(Duration.ofSeconds(10), running.mine.elapsed)
        assertEquals(tracker.progress.value.averagePace, running.mine.averagePace)
        assertEquals(null, running.opponentConnection, "기록 상대는 연결 상태가 없다")
    }

    @Test
    fun `랜덤 대결 - 상대 연결 상태를 진행 상태에 담고 상대 메시지가 내 값을 지우지 않는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = null))
        runCurrent()
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 15.0)
        runCurrent()
        live.channel.messages.emit(
            OpponentProgress(3, "상대", Distance(10), Duration.ofSeconds(5), 5, OpponentProgress.Connection.CHECKING)
        )
        runCurrent()
        val running = assertIs<BattleRunState.Running>(coordinator.state.value)
        assertEquals(OpponentProgress.Connection.CHECKING, running.opponentConnection)
        assertEquals(Duration.ofSeconds(5), running.mine.elapsed)
        assertEquals(tracker.progress.value.distance, running.mine.distance)
    }

    @Test
    fun `TC-6408 상대 연결 확인 중이면 마지막 격차를 두고 근접 알림을 멈춘 뒤 연결되면 새 격차로 잇는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = null))
        runCurrent()
        tracker.runTo(1, 0.0)
        tracker.runTo(7, 20.0)
        runCurrent()
        val connected = OpponentProgress.Connection.CONNECTED
        live.channel.messages.emit(OpponentProgress(3, "상대", Distance(10), Duration.ofSeconds(7), 20, connected))
        runCurrent()
        val checking = OpponentProgress.Connection.CHECKING
        live.channel.messages.emit(OpponentProgress(3, "상대", Distance(10), Duration.ofSeconds(8), 35, checking))
        runCurrent()
        val stale = assertIs<BattleRunState.Running>(coordinator.state.value)
        assertEquals(20, stale.gapMeters)
        assertEquals(checking, stale.opponentConnection)
        assertEquals(null, watch.proximity.last())
        live.channel.messages.emit(OpponentProgress(3, "상대", Distance(10), Duration.ofSeconds(9), 30, connected))
        runCurrent()
        assertEquals(30, assertIs<BattleRunState.Running>(coordinator.state.value).gapMeters)
        assertEquals(30, watch.proximity.last()?.gapMeters)
    }

    @Test
    fun `종료 카운트다운 중에도 내 거리·경과는 계속 갱신한다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        live.channel.messages.emit(BattleStart(battleId = 3, startAt = T0, opponentTrack = null))
        runCurrent()
        live.channel.messages.emit(CountdownStarted(3, "상대", T0.plusSeconds(60)))
        runCurrent()
        tracker.runTo(1, 0.0)
        tracker.runTo(8, 20.0)
        runCurrent()
        val closing = assertIs<BattleRunState.Closing>(coordinator.state.value)
        assertEquals(Duration.ofSeconds(8), closing.mine.elapsed)
        assertEquals(tracker.progress.value.distance, closing.mine.distance)
    }

    @Test
    fun `기록 대결 측정은 상대 이름을 진행 알림에 싣고 경로선을 내 측정값에 옮긴다(D-258, D-261)`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 20.0)
        runCurrent()
        val notice = assertIs<ProgressNotice.Battle>(tracker.notice.value)
        assertEquals("10/02의 나", notice.opponentName)
        assertEquals(Distance(100), notice.progressEnd)
        val mine = assertIs<BattleRunState.Running>(coordinator.state.value).mine
        assertEquals(2, mine.route.points.size)
    }

    @Test
    fun `그만두기는 측정 중이면 바로 서버에 보고한다(잠금 없음, D-256)`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        runCurrent()
        coordinator.forfeit()
        runCurrent()
        assertTrue(log.calls.any { it.startsWith("battle.forfeit") }, "${log.calls}")
    }

    @Test
    fun `매칭 대기 이탈 - 화면이 사라져도 앱 범위에서 대기열 취소를 보낸다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.abandonMatching()
        runCurrent()
        assertEquals(listOf("battle.cancel"), log.calls)
        assertTrue(parentJob.children.none(), "보낸 뒤 남는 작업이 없다")
    }

    @Test
    fun `매칭 요청 중에 이탈하면 요청이 끝난 뒤에 대기열 취소를 보낸다`() = runTest {
        val (_, coordinator) = setUp()
        repository.requestGate = CompletableDeferred()
        backgroundScope.launch { coordinator.requestMatch(Distance(3_000)) }
        runCurrent()
        coordinator.abandonMatching()
        runCurrent()
        assertEquals(listOf("battle.requestMatch:3000"), log.calls, "요청보다 취소가 먼저 가지 않는다")
        repository.requestGate?.complete(Unit)
        runCurrent()
        assertEquals(listOf("battle.requestMatch:3000", "battle.cancel"), log.calls)
    }

    @Test
    fun `이탈 취소를 다시 보내는 중에 새로 요청하면 남은 재시도를 멈춘다`() = runTest {
        val (_, coordinator) = setUp()
        repository.cancelFailure = AppError.Network
        coordinator.abandonMatching()
        runCurrent()
        backgroundScope.launch { coordinator.requestMatch(Distance(5_000)) }
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf("battle.cancel", "battle.requestMatch:5000"), log.calls, "새 대기열 등록을 지우지 않는다")
    }

    private fun matchedBattle(battleId: Long = 9, type: BattleHistory.Type = BattleHistory.Type.RANDOM) =
        ActiveBattle(battleId, type, "WAITING_START", Distance(5_000), "바람처럼", startAt = null)

    @Test
    fun `S15P31A207-278 이탈 취소가 ALREADY_MATCHED로 거부되면 진행 중인 대결을 찾아 그만두기를 보낸다`() = runTest {
        val (_, coordinator) = setUp()
        repository.cancelFailure = AppError.Rejected("ALREADY_MATCHED")
        repository.activeResults += Outcome.Success(matchedBattle())
        coordinator.abandonMatching()
        runCurrent()
        assertEquals(listOf("battle.cancel", "battle.active", "battle.forfeit:9"), log.calls)
        assertTrue(parentJob.children.none(), "보낸 뒤 남는 작업이 없다")
    }

    @Test
    fun `S15P31A207-278 짝을 잡고 대결을 만드는 중이라 대결이 아직 없으면 1초 2초 뒤 다시 찾는다`() = runTest {
        val (_, coordinator) = setUp()
        repository.cancelFailure = AppError.Rejected("ALREADY_MATCHED")
        repository.activeResults += Outcome.Success(null)
        repository.activeResults += Outcome.Failure(AppError.Network)
        repository.activeResults += Outcome.Success(matchedBattle(battleId = 11))
        coordinator.abandonMatching()
        runCurrent()
        assertEquals(listOf("battle.cancel", "battle.active"), log.calls)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(
            listOf("battle.cancel", "battle.active", "battle.active", "battle.active", "battle.forfeit:11"),
            log.calls
        )
    }

    @Test
    fun `S15P31A207-278 끝내 대결이 없거나 매칭으로 생길 수 없는 대결이면 그만두기를 보내지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        repository.cancelFailure = AppError.Rejected("ALREADY_MATCHED")
        coordinator.abandonMatching()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf("battle.cancel", "battle.active", "battle.active", "battle.active"), log.calls)

        log.calls.clear()
        repository.activeResults += Outcome.Success(matchedBattle(type = BattleHistory.Type.FRIEND))
        coordinator.abandonMatching()
        runCurrent()
        assertEquals(listOf("battle.cancel", "battle.active"), log.calls, "친구 대결은 이 대기열에서 생긴 대결이 아니다")
    }

    @Test
    fun `S15P31A207-278 이 프로세스가 맡은 대결이면 이탈 취소가 거부돼도 그만두기를 보내지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(MatchFound(9, "바람처럼", "SILVER", Distance(5_000), null, null, 0))
        runCurrent()
        repository.cancelFailure = AppError.Rejected("ALREADY_MATCHED")
        repository.activeResults += Outcome.Success(matchedBattle())
        coordinator.abandonMatching()
        runCurrent()
        assertEquals(listOf("battle.cancel", "battle.active"), log.calls)
    }

    @Test
    fun `S15P31A207-278 그만두기를 보내는 중에 새로 요청하면 그만두기가 끝난 뒤에 요청한다`() = runTest {
        val (_, coordinator) = setUp()
        repository.cancelFailure = AppError.Rejected("ALREADY_MATCHED")
        repository.activeResults += Outcome.Success(null)
        repository.activeResults += Outcome.Success(matchedBattle())
        coordinator.abandonMatching()
        runCurrent()
        backgroundScope.launch { coordinator.requestMatch(Distance(5_000)) }
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(
            listOf("battle.cancel", "battle.active", "battle.active", "battle.forfeit:9", "battle.requestMatch:5000"),
            log.calls,
            "남은 대결이 새 매칭 요청을 막지 않게 그만두기를 먼저 끝낸다"
        )
    }

    @Test
    fun `S15P31A207-278 이탈 그만두기를 네트워크로 끝내 보내지 못하면 남겨 두고 다시 보낼 때 보낸 뒤 지운다`() = runTest {
        val (_, coordinator) = setUp()
        repository.cancelFailure = AppError.Rejected("ALREADY_MATCHED")
        repository.activeResults += Outcome.Success(matchedBattle())
        repository.reportFailure = AppError.Network
        coordinator.abandonMatching()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(PendingForfeit(9, T0), forfeits.pending, "찾은 대결 번호를 적어 남긴다")

        log.calls.clear()
        repository.reportFailure = null
        coordinator.settlePendingForfeit()
        assertEquals(listOf("battle.forfeit:9"), log.calls, "번호를 알면 찾지 않고 보낸다")
        assertNull(forfeits.pending)
    }

    @Test
    fun `S15P31A207-278 진행 중인 대결을 끝내 읽지 못하면 번호 없이 남기고 다시 보낼 때 찾아서 보낸다`() = runTest {
        val (_, coordinator) = setUp()
        repository.cancelFailure = AppError.Rejected("ALREADY_MATCHED")
        repeat(3) { repository.activeResults += Outcome.Failure(AppError.Network) }
        coordinator.abandonMatching()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(PendingForfeit(null, T0), forfeits.pending)

        log.calls.clear()
        repository.activeResults += Outcome.Success(matchedBattle(battleId = 11))
        coordinator.settlePendingForfeit()
        assertEquals(listOf("battle.active", "battle.forfeit:11"), log.calls)
        assertNull(forfeits.pending)
    }

    @Test
    fun `S15P31A207-278 남은 그만두기가 기한을 넘었으면 보내지 않고 지운다`() = runTest {
        val (_, coordinator) = setUp()
        forfeits.pending = PendingForfeit(9, T0)
        clock.now = T0.plus(Duration.ofHours(1)).plusSeconds(1)
        coordinator.settlePendingForfeit()
        assertEquals(emptyList(), log.calls)
        assertNull(forfeits.pending)
    }

    @Test
    fun `S15P31A207-278 남은 그만두기가 있으면 새 매칭 요청 전에 먼저 보낸다`() = runTest {
        val (_, coordinator) = setUp()
        forfeits.pending = PendingForfeit(9, T0)
        coordinator.requestMatch(Distance(5_000))
        assertEquals(listOf("battle.forfeit:9", "battle.requestMatch:5000"), log.calls)
        assertNull(forfeits.pending)
    }

    @Test
    fun `S15P31A207-278 성사 뒤 출발 전 이탈은 회를 기록 없이 닫고 그 대결에 그만두기를 보낸다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(MatchFound(9, "바람처럼", "SILVER", Distance(5_000), null, null, 0))
        runCurrent()
        assertFalse(coordinator.abandonMatched(10), "다른 대결 번호면 닫지 않는다")
        assertTrue(coordinator.isRunning(9))

        repository.reportFailure = AppError.Network
        assertTrue(coordinator.abandonMatched(9))
        assertEquals(BattleRunState.Idle, coordinator.state.value)
        assertFalse(coordinator.isRunning(9))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(List(3) { "battle.forfeit:9" }, log.calls)
        assertEquals(PendingForfeit(9, T0), forfeits.pending, "보내지 못했으면 남긴다")

        log.calls.clear()
        repository.reportFailure = null
        coordinator.settlePendingForfeit()
        assertEquals(listOf("battle.forfeit:9"), log.calls)
        assertNull(forfeits.pending)
    }

    @Test
    fun `D-373 워치가 측정 중이면 매칭 요청을 서버에 보내지 않고 WATCH_MEASURING으로 거부한다`() = runTest {
        val (_, coordinator) = setUp()
        watch.watchMeasuring = true

        val result = coordinator.requestMatch(Distance(1_000))

        assertEquals(Outcome.Failure(AppError.Rejected(AppError.Rejected.WATCH_MEASURING)), result)
        assertTrue(log.calls.isEmpty(), "서버 대기열에 등록하지 않는다")
    }

    @Test
    fun `D-373 워치가 측정 중이면 친구 초대를 만들지 않고 WATCH_MEASURING으로 거부한다`() = runTest {
        val (_, coordinator) = setUp()
        watch.watchMeasuring = true

        val result = coordinator.createInvite(Distance(1_000))

        assertEquals(Outcome.Failure(AppError.Rejected(AppError.Rejected.WATCH_MEASURING)), result)
        assertTrue(log.calls.isEmpty(), "서버에 초대를 만들지 않는다")
    }

    @Test
    fun `D-373 대결 화면이 시작 전에 워치 측정 중인지 읽는다`() = runTest {
        val (_, coordinator) = setUp()
        assertFalse(coordinator.isWatchMeasuring)

        watch.watchMeasuring = true

        assertTrue(coordinator.isWatchMeasuring)
    }

    private val offlineSelf =
        OfflineRecordBattle(
            kind = RecordBattleKind.SELF,
            distance = Distance(100),
            celebrityId = null,
            track = OpponentTrack.uniform(Distance(100), Duration.ofSeconds(50))
        )

    @Test
    fun `D-377 오프라인 기록 대결 - 3초 카운트다운 뒤 측정하고 구간 기록으로 격차를 내며 서버에 보고하지 않는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onOfflineRecordBattle(offlineSelf)
        runCurrent()
        assertIs<BattleRunState.Countdown>(coordinator.state.value)
        assertEquals(0, host.started)

        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(10, 30.0)
        runCurrent()

        assertEquals(1, host.started)
        // 10초에 나 30m, 상대(50초 100m 균등) 20m → +10m
        val running = assertIs<BattleRunState.Running>(coordinator.state.value)
        assertEquals(Distance(20), running.opponentDistance)
        assertEquals(10, running.gapMeters)
        assertTrue(live.channel.sent.isEmpty())
        assertTrue(log.calls.none { it.startsWith("battle.") }, "서버 대결 REST를 부르지 않는다: ${log.calls}")
    }

    @Test
    fun `D-377 오프라인 기록 대결 - 도착하면 대결 ID 없는 자유런 기록으로 저장하고 Finished로 닫는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onOfflineRecordBattle(offlineSelf)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(30, 101.0)
        runCurrent()

        assertIs<BattleRunState.Finished>(coordinator.state.value)
        val run = pending.runs.single()
        assertEquals(RunMode.FREE, run.mode)
        assertEquals(CardKind.FREE_RUN, run.cardKind)
        assertNull(run.battleId)
        assertEquals(listOf("pending.save", "upload.requestUpload"), log.calls)
        assertEquals(1, host.stopped)
        assertTrue(parentJob.children.none())
    }

    @Test
    fun `D-377 오프라인 기록 대결 그만두기 - 서버에 보고하지 않고 기록을 남긴다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onOfflineRecordBattle(offlineSelf)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(5, 12.0)
        runCurrent()

        coordinator.forfeit()
        runCurrent()

        assertEquals(listOf("pending.save", "upload.requestUpload"), log.calls)
        assertIs<BattleRunState.Finished>(coordinator.state.value)
    }

    @Test
    fun `D-377 오프라인 기록 대결 취소 - 서버에 보고하지 않고 기록을 버린다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onOfflineRecordBattle(offlineSelf)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        runCurrent()

        coordinator.cancel()
        runCurrent()

        assertTrue(log.calls.isEmpty(), "${log.calls}")
        assertIs<BattleRunState.Discarded>(coordinator.state.value)
        assertFalse(tracker.isMeasuring)
    }

    @Test
    fun `FR-245 워치에서 시작한 랜덤 대결은 시작 기기 WATCH로 기록한다`() = runTest {
        val (tracker, coordinator) = setUp()
        startRandom(tracker, coordinator, RunStartSpec.StartDevice.WATCH)

        coordinator.forfeit()
        runCurrent()

        val run = pending.runs.single()
        assertEquals(RunMode.BATTLE, run.mode)
        assertEquals(RunStartSpec.StartDevice.WATCH, run.startDevice)
    }

    @Test
    fun `FR-245 워치에서 시작한 서버 셀프런과 셀럽런은 시작 기기 WATCH로 기록한다`() = runTest {
        val (tracker, coordinator) = setUp()
        listOf(selfStart, selfStart.copy(battleId = 8, cardKind = CardKind.CELEB_RUN)).forEach { start ->
            coordinator.onRecordBattleStart(start, RunStartSpec.StartDevice.WATCH)
            runForfeitedRecord(tracker, coordinator)
        }

        assertEquals(listOf(CardKind.SELF_RUN, CardKind.CELEB_RUN), pending.runs.map { it.cardKind })
        assertEquals(List(2) { RunStartSpec.StartDevice.WATCH }, pending.runs.map { it.startDevice })
    }

    @Test
    fun `폰에서 시작한 랜덤 대결과 기록 대결은 그대로 시작 기기 PHONE으로 기록한다`() = runTest {
        val (tracker, coordinator) = setUp()
        startRandom(tracker, coordinator)
        coordinator.forfeit()
        runCurrent()
        coordinator.acknowledgeFinished()

        coordinator.onRecordBattleStart(selfStart)
        runForfeitedRecord(tracker, coordinator)

        assertEquals(List(2) { RunStartSpec.StartDevice.PHONE }, pending.runs.map { it.startDevice })
    }

    @Test
    fun `D-377 오프라인 기록 대결은 시작 기기 WATCH로 기록한다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onOfflineRecordBattle(offlineSelf)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        tracker.runTo(30, 101.0)
        runCurrent()

        assertEquals(RunStartSpec.StartDevice.WATCH, pending.runs.single().startDevice)
    }

    /** 기록 대결 카운트다운을 지나 지금 시각부터 조금 달린 뒤 그만두고 결과를 확인한다(기록이 남는다). */
    private fun TestScope.runForfeitedRecord(tracker: RunTracker, coordinator: BattleRunCoordinator) {
        advanceTimeBy(3_001)
        val second = clock.now.epochSecond - T0.epochSecond
        tracker.runTo(second + 1, 0.0)
        tracker.runTo(second + 5, 12.0)
        runCurrent()
        coordinator.forfeit()
        runCurrent()
        coordinator.acknowledgeFinished()
    }

    private val match =
        MatchFound(
            battleId = 3,
            opponentName = "상대",
            opponentTier = "SILVER",
            distance = Distance(1_000),
            myBest = null,
            opponentBest = Duration.ofMinutes(5),
            animationSeconds = 3
        )
}
