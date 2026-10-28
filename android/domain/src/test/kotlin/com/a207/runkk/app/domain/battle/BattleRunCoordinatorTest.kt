package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.TrackPoint
import com.a207.runkk.app.domain.live.TrackTable
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.sample
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsSnapshot
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
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    override suspend fun result(battleId: Long): Outcome<BattleResult> = error("unused")

    override suspend fun history(filter: BattleHistory.Type?, cursor: Long?): Outcome<BattleHistory> = error("unused")

    override suspend fun matchStatus(): Outcome<MatchAcceptance> = error("unused")
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
    private val settings = FakeSettingsRepository()
    private val watch = FakeWatchGateway()
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
        assertIs<BattleRunState.Finished>(coordinator.state.value)
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
    fun `화면 잠금 - 조정자가 RunTracker 잠금 연산을 위임하고 누름 종류별로 해제한다(D-49)`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        runCurrent()
        coordinator.lockScreen()
        runCurrent()
        assertTrue(assertIs<BattleRunState.Running>(coordinator.state.value).mine.isLocked)
        coordinator.onUnlockTapped()
        runCurrent()
        assertEquals(Guidance.Kind.UNLOCK_HINT, output.played.last().kind, "짧게 누르면 안내만")
        assertTrue(assertIs<BattleRunState.Running>(coordinator.state.value).mine.isLocked)
        coordinator.onUnlockLongPressed()
        runCurrent()
        assertEquals(false, assertIs<BattleRunState.Running>(coordinator.state.value).mine.isLocked)
    }

    @Test
    fun `잠금 중 그만두기는 조정자에서도 받지 않는다(FR-182)`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        runCurrent()
        coordinator.lockScreen()
        runCurrent()
        coordinator.forfeit()
        runCurrent()
        assertTrue(log.calls.none { it.startsWith("battle.forfeit") }, "잠긴 화면에서 온 그만두기: ${log.calls}")
        assertIs<BattleRunState.Running>(coordinator.state.value)
        coordinator.onUnlockLongPressed()
        runCurrent()
        coordinator.forfeit()
        runCurrent()
        assertTrue(log.calls.any { it.startsWith("battle.forfeit") }, "해제 뒤에는 그만둔다")
    }

    @Test
    fun `측정 전에는 잠그지 않는다`() = runTest {
        val (_, coordinator) = setUp()
        coordinator.onMatched(match)
        runCurrent()
        coordinator.lockScreen()
        runCurrent()
        assertEquals(BattleRunState.Matched(match), coordinator.state.value)
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

    @Test
    fun `잠근 뒤 늦게 처리된 진행값이 잠금을 되돌리지 않는다`() = runTest {
        val (tracker, coordinator) = setUp()
        coordinator.onRecordBattleStart(selfStart)
        advanceTimeBy(3_001)
        tracker.runTo(1, 0.0)
        runCurrent()
        coordinator.lockScreen()
        runCurrent()
        coordinator.onProgress(RunProgress.INITIAL.copy(elapsed = Duration.ofSeconds(30), isLocked = false))
        runCurrent()
        assertTrue(assertIs<BattleRunState.Running>(coordinator.state.value).mine.isLocked)
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
