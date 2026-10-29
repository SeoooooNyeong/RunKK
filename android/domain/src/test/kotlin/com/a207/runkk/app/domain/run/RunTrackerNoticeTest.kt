package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakeGuidanceOutput
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 진행 알림 값·알림 일시정지·경로선(D-258, D-260, D-261): 시험 문서 12.3.1 TC-9610, TC-9617~TC-9619.
 * 알림 버튼·워치 요청·폰 버튼의 정지·재개가 같은 RunSession.pause·resume으로 모이는지 본다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunTrackerNoticeTest {
    private val log = CallLog()
    private val pending = FakePendingRunRepository(log)
    private val watch = FakeWatchGateway()
    private val output = FakeGuidanceOutput()
    private val host = FakeMeasurementHost()
    private val clock = TestClock()

    private fun TestScope.tracker(): RunTracker = runTracker(
        pending,
        FakeUploadScheduler(log),
        watch,
        output,
        FakeLocationPermission(),
        host,
        FakeSettingsRepository(),
        clock,
        backgroundScope
    )

    private fun TestScope.started(spec: RunStartSpec = freeSpec(PlanSnapshot(Distance(5_000), null))): RunTracker {
        clock.now = T0
        val tracker = tracker()
        tracker.startRun(spec)
        runCurrent()
        return tracker
    }

    @Test
    fun `TC-9610 notice는 시작 전 null, tick마다 새 값, 종료·취소 뒤 null이다`() = runTest {
        val tracker = tracker()
        runCurrent()
        assertNull(tracker.notice.value)
        clock.now = T0
        tracker.startRun(freeSpec())
        val seen = mutableListOf<ProgressNotice?>()
        backgroundScope.launch { tracker.notice.collect { seen += it } }
        runCurrent()
        seen.clear()
        for (s in 1..3L) {
            tracker.tick(T0.plusSeconds(s))
            runCurrent()
        }
        assertEquals(listOf(1L, 2L, 3L), seen.map { it?.elapsed?.seconds })
        tracker.onLocationSample(sample(1, 0.0))
        tracker.onLocationSample(sample(200, 900.0))
        clock.now = T0.plusSeconds(200)
        tracker.onFinishLongPressed()
        assertNull(tracker.notice.value, "종료 뒤 null")

        tracker.startRun(freeSpec())
        tracker.tick(T0.plusSeconds(201))
        assertIs<ProgressNotice.Free>(tracker.notice.value)
        assertTrue(tracker.discardRun())
        assertNull(tracker.notice.value, "취소 뒤 null")
    }

    @Test
    fun `TC-9617 알림 일시정지·재개는 멱등이고 정지 구간·워치 표시·안내가 한 번씩이다`() = runTest {
        val tracker = started()
        tracker.onLocationSample(sample(1, 0.0))
        tracker.onLocationSample(sample(201, 1_000.0))
        clock.now = T0.plusSeconds(600)
        tracker.onNotificationPauseTapped()
        assertTrue(assertIs<ProgressNotice.Free>(tracker.notice.value).isPaused)
        assertTrue(watch.published.last().isPaused)
        val guidances = output.played.count { it.message == GuidanceMessage.PAUSED }
        assertEquals(1, guidances)
        clock.now = T0.plusSeconds(610)
        tracker.onNotificationPauseTapped()
        assertEquals(1, output.played.count { it.message == GuidanceMessage.PAUSED }, "연타는 변화 0")
        clock.now = T0.plusSeconds(660)
        tracker.onNotificationResumeTapped()
        assertFalse(assertIs<ProgressNotice.Free>(tracker.notice.value).isPaused)
        tracker.onNotificationResumeTapped()
        assertEquals(1, output.played.count { it.message == GuidanceMessage.RESUMED }, "RUNNING에서 재개는 변화 0")
        clock.now = T0.plusSeconds(700)
        val run = assertIs<FinishOutcome.Saved>(tracker.onFinishLongPressed()).run
        assertEquals(listOf(PauseInterval(T0.plusSeconds(600), T0.plusSeconds(660))), run.pauses)
    }

    @Test
    fun `TC-9618 대결·크루런 중 알림 일시정지는 무시한다`() = runTest {
        for (mode in listOf(RunMode.BATTLE, RunMode.CREW_RUN)) {
            val tracker = started(
                RunStartSpec(mode = mode, cardKind = CardKind.BATTLE_RUN, battleId = 3, recordId = "b-$mode")
            )
            tracker.onNotificationPauseTapped()
            assertFalse(tracker.progress.value.isPaused, "$mode")
            assertTrue(output.played.none { it.kind == Guidance.Kind.PAUSE })
            tracker.endRun()
        }
    }

    @Test
    fun `TC-9619 워치·알림·폰 버튼의 정지는 같은 상태와 정지 구간을 만든다`() = runTest {
        val results = mutableListOf<List<PauseInterval>>()
        val inputs: List<suspend (RunTracker) -> Unit> = listOf(
            { _ ->
                watch.pauseRequests.emit(WatchGateway.PauseAction.PAUSE)
                runCurrent()
            },
            { t -> t.onNotificationPauseTapped() },
            { t -> t.onPauseTapped() }
        )
        for (pause in inputs) {
            pending.runs.clear()
            val tracker = started()
            tracker.onLocationSample(sample(1, 0.0))
            tracker.onLocationSample(sample(201, 1_000.0))
            clock.now = T0.plusSeconds(300)
            pause(tracker)
            assertTrue(tracker.progress.value.isPaused)
            clock.now = T0.plusSeconds(360)
            val run = assertIs<FinishOutcome.Saved>(tracker.onFinishLongPressed()).run
            results += run.pauses
            runCurrent()
        }
        assertEquals(1, results.distinct().size, "$results")
        assertEquals(listOf(PauseInterval(T0.plusSeconds(300), T0.plusSeconds(360))), results.first())
    }

    @Test
    fun `대결 알림은 상대 이름과 알려진 격차를 싣고 연결 확인 중이면 격차를 비운다`() = runTest {
        val tracker = started(
            RunStartSpec(
                mode = RunMode.BATTLE,
                cardKind = CardKind.BATTLE_RUN,
                battleId = 3,
                recordId = "b-1",
                todayPlan = PlanSnapshot(Distance(3_000), null),
                opponentName = "바람처럼"
            )
        )
        tracker.onGapChanged(42)
        tracker.tick(T0.plusSeconds(1))
        val battle = assertIs<ProgressNotice.Battle>(tracker.notice.value)
        assertEquals("바람처럼", battle.opponentName)
        assertEquals(42, battle.gapMeters)
        assertEquals(Distance(3_000), battle.progressEnd)
        tracker.onGapChanged(null)
        tracker.tick(T0.plusSeconds(2))
        assertNull(assertIs<ProgressNotice.Battle>(tracker.notice.value).gapMeters)
    }

    @Test
    fun `routeLine은 받아들인 포인트로 늘고 정지 중 포인트는 넣지 않으며 끝나면 비운다`() = runTest {
        val tracker = started()
        tracker.onLocationSample(sample(1, 0.0))
        tracker.onLocationSample(sample(5, 20.0))
        assertEquals(2, tracker.routeLine.value.points.size)
        assertEquals(20.0, tracker.routeLine.value.lengthMeters, 0.01)
        clock.now = T0.plusSeconds(6)
        tracker.onPauseTapped()
        tracker.onLocationSample(sample(7, 25.0))
        assertEquals(2, tracker.routeLine.value.points.size)
        tracker.endRun()
        assertEquals(0, tracker.routeLine.value.points.size, "끝나면 비운다")
        tracker.startRun(freeSpec())
        assertEquals(0, tracker.routeLine.value.points.size)
        assertEquals(Duration.ZERO, tracker.progress.value.elapsed)
    }
}
