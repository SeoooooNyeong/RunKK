package com.a207.runkk.watch.battle

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.OfflineRecordBattle
import com.a207.runkk.app.domain.battle.RandomBattleGate
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.watch.cache.OpponentRecordCache
import com.a207.runkk.watch.link.MatchWaiter
import com.a207.runkk.watch.run.LocalWatchGateway
import com.a207.runkk.watch.testing.FakeBattleRepository
import com.a207.runkk.watch.testing.FakeGuidanceOutput
import com.a207.runkk.watch.testing.FakeLiveChannelProvider
import com.a207.runkk.watch.testing.FakePendingRunRepository
import com.a207.runkk.watch.testing.FakeSettingsRepository
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.alertPlayer
import com.a207.runkk.watch.testing.celebrityListItem
import com.a207.runkk.watch.testing.celebrityTrack
import com.a207.runkk.watch.testing.runTracker
import com.a207.runkk.watch.testing.selfTrack
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** WatchBattleFlow: 매칭 대기 연결·성사·실패, 대결 상태 → 화면 상태, 출발까지 남은 초, 전송 뒤 상대 기록 다시 받기 신호(3.5, D-386). */
class WatchBattleFlowTest {
    private val live = FakeLiveChannelProvider()
    private val battles = FakeBattleRepository()

    private class Harness(val flow: WatchBattleFlow, val coordinator: BattleRunCoordinator, val pending: PendingRuns)

    /** 미전송 기록 목록을 흉내 낸다(observePending이 값을 바꿔 낸다). */
    private class PendingRuns : FakePendingRunRepositoryWithFlow()

    private fun TestScope.harness(): Harness {
        val clock = SchedulerClock(testScheduler)
        val settings = FakeSettingsRepository()
        val tracker = runTracker(LocalWatchGateway(alertPlayer(), settings, clock), settings = settings)
        val coordinator = BattleRunCoordinator(tracker, live, battles, FakeGuidanceOutput(), clock, backgroundScope)
        val cache = OpponentRecordCache(
            File(createTempDirectory("battle-flow").toFile(), "opponent-records.json"),
            battles,
            clock,
            backgroundScope,
            StandardTestDispatcher(testScheduler)
        )
        val pending = PendingRuns()
        val flow = WatchBattleFlow(
            coordinator,
            battles,
            cache,
            MatchWaiter(live, backgroundScope),
            pending,
            RecordBattleMemory(),
            clock
        )
        return Harness(flow, coordinator, pending)
    }

    @Test
    fun `매칭 대기 채널을 연결한 뒤 요청하고 성사되면 조정자가 성사 상태가 된다`() = runTest {
        val h = harness()

        val accepted = h.flow.startMatching(Distance(1_000))
        assertIs<Outcome.Success<MatchAcceptance>>(accepted)
        assertEquals(1, live.opened)
        assertEquals(listOf(Distance(1_000)), battles.matchRequests)

        val waiting = async { h.flow.awaitMatch() }
        runCurrent()
        live.server.tryEmit(MatchFound(9, "민준", "GOLD", Distance(1_000), null, null, 3, Gender.MALE))
        runCurrent()

        assertEquals(Outcome.Success(Unit), waiting.await())
        assertIs<BattleRunState.Matched>(h.coordinator.state.value)
        assertEquals(BattleStep.Matched(9, Distance(1_000), "민준", Gender.MALE), h.flow.step())
    }

    @Test
    fun `TC-5867 워치 잠금 판단은 공용 판단과 같다`() = runTest {
        val h = harness()

        listOf("UNRANKED", "GOLD", null).forEach { tier ->
            assertEquals(RandomBattleGate.isWatchRandomLocked(tier), h.flow.isRandomLocked(tier), "티어 $tier")
        }
        assertTrue(h.flow.isRandomLocked("UNRANKED"))
        assertFalse(h.flow.isRandomLocked(null))
    }

    @Test
    fun `기본 대결 거리는 서버 기본값이고 대기 중이면 대기 거리이며 읽지 못하면 null이다(FR-109)`() = runTest {
        val h = harness()
        assertNull(h.flow.defaultDistance())

        battles.matchStatusAnswer =
            Outcome.Success(MatchAcceptance(isWaiting = false, distance = null, defaultDistance = Distance(3_000)))
        assertEquals(Distance(3_000), h.flow.defaultDistance())

        battles.matchStatusAnswer = Outcome.Success(
            MatchAcceptance(isWaiting = true, distance = Distance(10_000), defaultDistance = Distance(3_000))
        )
        assertEquals(Distance(10_000), h.flow.defaultDistance())

        battles.matchStatusAnswer =
            Outcome.Success(MatchAcceptance(isWaiting = true, distance = null, defaultDistance = Distance(3_000)))
        assertEquals(Distance(3_000), h.flow.defaultDistance())
    }

    @Test
    fun `대기 채널을 연결하지 못하면 매칭을 요청하지 않는다`() = runTest {
        live.isConnectFailing = true
        val h = harness()

        val accepted = h.flow.startMatching(Distance(3_000))

        assertEquals(Outcome.Failure(AppError.Network), accepted)
        assertTrue(battles.matchRequests.isEmpty())
        assertEquals(1, live.closed)
    }

    @Test
    fun `서버가 매칭을 실패로 끝내면 그 사유 코드로 실패하고 대기 채널을 닫는다(취소는 보내지 않는다, WR12-1)`() = runTest {
        val h = harness()
        h.flow.startMatching(Distance(3_000))
        val waiting = async { h.flow.awaitMatch() }
        runCurrent()

        live.server.tryEmit(MatchFailed("NO_OPPONENT", "상대 없음"))
        runCurrent()

        assertEquals(Outcome.Failure(AppError.Rejected("NO_OPPONENT")), waiting.await())
        assertEquals(1, live.closed)
        assertEquals(0, battles.cancels)
    }

    @Test
    fun `대기 중 취소는 대기 채널을 닫고 서버 대기열에서 뺀다`() = runTest {
        val h = harness()
        h.flow.startMatching(Distance(3_000))

        h.flow.cancelMatching()
        runCurrent()

        assertEquals(1, live.closed)
        assertEquals(1, battles.cancels)
    }

    @Test
    fun `대결 상태를 화면이 쓰는 단계로 옮긴다 측정 중은 거리 차와 추월 안내만 남긴다(R75 W30)`() {
        assertEquals(BattleStep.Idle, WatchBattleFlow.stepOf(BattleRunState.Idle))
        assertEquals(BattleStep.Finished(3), WatchBattleFlow.stepOf(BattleRunState.Finished(3, null)))
        assertEquals(BattleStep.Discarded, WatchBattleFlow.stepOf(BattleRunState.Discarded(3)))
        assertEquals(
            BattleStep.Running(gapMeters = -6, lead = null),
            WatchBattleFlow.stepOf(
                BattleRunState.Running(3, gapMeters = -6, opponentDistance = null, lastGuidance = null)
            )
        )
        assertEquals(
            BattleStep.Countdown(3, Instant.EPOCH),
            WatchBattleFlow.stepOf(BattleRunState.Countdown(3, Instant.EPOCH))
        )
        assertTrue(BattleStep.Countdown(3, Instant.EPOCH).isOpen && BattleStep.Closing.isOpen)
        assertFalse(BattleStep.Idle.isOpen || BattleStep.Finished(3).isOpen)
    }

    @Test
    fun `출발까지 남은 초는 올림이고 지났으면 0이다`() = runTest {
        val h = harness()
        val now = SchedulerClock(testScheduler).instant()

        assertEquals(3, h.flow.secondsUntil(now.plusMillis(2_100)))
        assertEquals(1, h.flow.secondsUntil(now.plusMillis(1)))
        assertEquals(0, h.flow.secondsUntil(now.minusSeconds(1)))
    }

    @Test
    fun `미전송 기록이 줄 때마다 한 번씩 알린다(SQ-46)`() = runTest {
        val h = harness()
        val sent = mutableListOf<Unit>()
        backgroundScope.launch { h.flow.uploadsSent().toList(sent) }
        runCurrent()

        listOf(2, 3, 1).forEach {
            h.pending.emit(it)
            runCurrent()
        }

        assertEquals(1, sent.size)
    }

    @Test
    fun `셀프런 새 최고 기록은 상대 기록의 완주 시간을 바꾼다(FR-260)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] =
            listOf(selfTrack(distanceMeters = 3_000, finishSeconds = 900))
        val h = harness()
        h.flow.refreshCache(memberId = 1)
        runCurrent()

        h.flow.onNewSelfBest(Distance(3_000), Duration.ofSeconds(880))

        assertEquals(Duration.ofSeconds(880), h.flow.selfCandidates().single().finishTime)
    }

    @Test
    fun `연 기록 대결의 상대는 회가 열려 있는 동안만 돌려주고 끝난 뒤 확인 전이어도 돌려주지 않는다(WR13-2)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(distanceMeters = 3_000, finishSeconds = 900))
        val h = harness()
        h.flow.refreshCache(memberId = 1)
        runCurrent()
        val opponent = h.flow.selfCandidates().single()

        h.flow.startRecordOffline(opponent)
        runCurrent()
        assertIs<BattleStep.Countdown>(h.flow.step())
        assertEquals(opponent, h.flow.activeRecord())

        // 화면이 결과를 확인(acknowledge)하기 전에 사라져 끝 상태가 남아 있어도 다음 재진입이 기록 대결로 보지 않는다.
        h.flow.forfeit()
        runCurrent()
        assertFalse(h.flow.step().isOpen)
        assertNull(h.flow.activeRecord())
    }

    @Test
    fun `셀럽 상대는 받아 둔 구간 기록이 없으면 받아서 돌려주고 그다음은 받지 않고 바로 돌려준다(W31)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = emptyList()
        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(celebrityListItem(1, 5_000, 1_500))
        battles.celebrityTracks[1] = Outcome.Success(celebrityTrack(1, 5_000, 1_500))
        val h = harness()
        h.flow.refreshCache(memberId = 1)
        runCurrent()
        val listed = h.flow.celebrityCandidates(Distance(5_000)).single()
        assertNull(h.flow.readyCelebrity(listed))

        val fetched = assertIs<Outcome.Success<RecordOpponent>>(h.flow.fetchCelebrity(listed)).value

        assertEquals("셀럽1", fetched.displayName)
        assertEquals(fetched, h.flow.readyCelebrity(listed))
        assertEquals(listOf(1L), battles.celebrityTrackCalls)
        h.flow.startRecordOffline(assertNotNull(h.flow.readyCelebrity(listed)))
        runCurrent()
        assertIs<BattleStep.Countdown>(h.flow.step())
    }

    @Test
    fun `셀럽 ID 없는 셀럽 기록은 받지 않고 거부다(W31)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = emptyList()
        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(celebrityListItem(1, 5_000, 1_500).copy(celebrityId = null))
        val h = harness()
        h.flow.refreshCache(memberId = 1)
        runCurrent()
        val listed = h.flow.celebrityCandidates(Distance(5_000)).single()

        assertIs<AppError.Rejected>(assertIs<Outcome.Failure>(h.flow.fetchCelebrity(listed)).error)
        assertTrue(battles.celebrityTrackCalls.isEmpty())
    }

    @Test
    fun `기록 대결이 끝나고 서버 시작이 실패해도 낡은 상대가 남지 않아 폰이 연 다른 회로 되살아나지 않는다(WR14-2)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(distanceMeters = 3_000, finishSeconds = 900))
        val h = harness()
        h.flow.refreshCache(memberId = 1)
        runCurrent()
        val opponent = h.flow.selfCandidates().single()
        h.flow.startRecordOffline(opponent)
        runCurrent()
        h.flow.forfeit()
        runCurrent()

        // 확인 전에 다음 기록 대결 시작이 실패해도(서버 거부) 이전 기억은 지워진다.
        assertIs<Outcome.Failure>(h.flow.startRecordOnline(opponent))
        // 폰이 연 다른 회가 열려도 이 워치의 기록 대결 맥락으로 보지 않는다.
        h.coordinator.onOfflineRecordBattle(
            OfflineRecordBattle(RecordBattleKind.SELF, Distance(3_000), null, opponent.cached.record.track)
        )
        runCurrent()
        assertTrue(h.flow.step().isOpen)
        assertNull(h.flow.activeRecord())
    }

    @Test
    fun `회가 끝났음을 알리면(forgetRecord) 상대 기억을 지운다(WR14-2)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(distanceMeters = 3_000, finishSeconds = 900))
        val h = harness()
        h.flow.refreshCache(memberId = 1)
        runCurrent()
        h.flow.startRecordOffline(h.flow.selfCandidates().single())
        runCurrent()
        assertNotNull(h.flow.activeRecord())

        h.flow.forgetRecord()

        assertNull(h.flow.activeRecord())
    }
}

/** observePending이 [emit]한 개수만큼의 기록 목록을 내는 미전송 기록 보관함. */
open class FakePendingRunRepositoryWithFlow : PendingRunRepository by FakePendingRunRepository() {
    private val runs = MutableStateFlow<List<CompletedRun>>(emptyList())

    fun emit(count: Int) {
        runs.value = List(count) { SAMPLE }
    }

    override fun observePending(): Flow<List<CompletedRun>> = runs

    private companion object {
        val SAMPLE = CompletedRun(
            recordId = "r",
            mode = RunMode.FREE,
            cardKind = CardKind.FREE_RUN,
            battleId = null,
            crewRunPostId = null,
            startedAt = Instant.EPOCH,
            elapsed = Duration.ZERO,
            distance = Distance.ZERO,
            route = emptyList(),
            unstable = emptyList(),
            heartRates = emptyList(),
            savedAt = Instant.EPOCH
        )
    }
}
