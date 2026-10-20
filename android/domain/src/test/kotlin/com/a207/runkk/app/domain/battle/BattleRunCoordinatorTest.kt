package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.TrackPoint
import com.a207.runkk.app.domain.live.TrackTable
import com.a207.runkk.app.domain.run.Guidance
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
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class FakeBattleRepository(private val log: CallLog) : BattleRepository {
    override suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance> = error("unused")

    override suspend fun cancel(): Outcome<MatchAcceptance> = error("unused")

    override suspend fun selfCandidates(): Outcome<RecordBattleCandidates> = error("unused")

    override suspend fun celebrityCandidates(distance: Distance?): Outcome<RecordBattleCandidates> = error("unused")

    override suspend fun startSelfBattle(distance: Distance): Outcome<RecordBattleStart> = error("unused")

    override suspend fun startCelebrityBattle(celebrityId: Long): Outcome<RecordBattleStart> = error("unused")

    override suspend fun markStarted(battleId: Long): Outcome<Unit> {
        log.add("battle.markStarted:$battleId")
        return Outcome.Success(Unit)
    }

    override suspend fun forfeit(battleId: Long): Outcome<Unit> {
        log.add("battle.forfeit:$battleId")
        return Outcome.Success(Unit)
    }

    override suspend fun result(battleId: Long): Outcome<BattleResult> = error("unused")

    override suspend fun history(filter: BattleHistory.Type?): Outcome<BattleHistory> = error("unused")
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
    private val parentJob = SupervisorJob()

    @AfterTest
    fun tearDown() {
        parentJob.cancel()
    }

    private fun TestScope.setUp(): Pair<RunTracker, BattleRunCoordinator> {
        val tracker =
            RunTracker(
                pending,
                FakeUploadScheduler(log),
                FakeWatchGateway(),
                output,
                FakeLocationPermission(),
                host,
                FakeSettingsRepository(),
                clock,
                backgroundScope
            )
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler) + parentJob)
        return tracker to BattleRunCoordinator(tracker, live, repository, output, clock, appScope)
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
    fun `그만두기 - forfeit 보고 뒤 endRun하고 범위를 닫는다`() = runTest {
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
