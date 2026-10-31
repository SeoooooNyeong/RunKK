package com.a207.runkk.watch.ui

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.battle.MatchAcceptance
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
        val flow = WatchBattleFlow(coordinator, battles, cache, MatchWaiter(live, backgroundScope), pending, clock)
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

        assertTrue(waiting.await())
        assertIs<BattleRunState.Matched>(h.coordinator.state.value)
        val matched = h.flow.toFlow(h.coordinator.state.value) as WatchFlow.Matched
        assertEquals("민준", matched.opponentName)
        assertEquals(Gender.MALE, matched.opponentGender)
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
    fun `서버가 매칭을 실패로 끝내면 거짓이고 대기 채널을 닫는다(취소는 보내지 않는다)`() = runTest {
        val h = harness()
        h.flow.startMatching(Distance(3_000))
        val waiting = async { h.flow.awaitMatch() }
        runCurrent()

        live.server.tryEmit(MatchFailed("NO_OPPONENT", "상대 없음"))
        runCurrent()

        assertFalse(waiting.await())
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
    fun `대결 상태를 화면 상태로 바꾼다 측정 중은 거리 차 대기와 끝은 null`() = runTest {
        val h = harness()

        assertNull(h.flow.toFlow(BattleRunState.Idle))
        assertNull(h.flow.toFlow(BattleRunState.Finished(3, null)))
        val running = h.flow.toFlow(
            BattleRunState.Running(3, gapMeters = -6, opponentDistance = null, lastGuidance = null)
        )
        assertEquals(-6, (running as WatchFlow.Running).gapMeters)
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

        assertEquals(Duration.ofSeconds(880), h.flow.selfCandidates().single().record.finishTime)
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
