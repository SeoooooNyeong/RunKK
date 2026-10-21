package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakeGuidanceOutput
import com.a207.runkk.app.domain.testing.FakeLocationPermission
import com.a207.runkk.app.domain.testing.FakeMeasurementHost
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import com.a207.runkk.app.domain.testing.FakeUploadScheduler
import com.a207.runkk.app.domain.testing.FakeWatchGateway
import com.a207.runkk.app.domain.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** RunTracker(6.1.14): 가짜 포트로 측정 흐름을 확인한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class RunTrackerTest {
    private val log = CallLog()
    private val pending = FakePendingRunRepository(log)
    private val scheduler = FakeUploadScheduler(log)
    private val watch = FakeWatchGateway()
    private val output = FakeGuidanceOutput()
    private val permission = FakeLocationPermission()
    private val host = FakeMeasurementHost()
    private val settings = FakeSettingsRepository()
    private val clock = TestClock()

    private fun tracker(scope: CoroutineScope) =
        RunTracker(RunArchive(pending, scheduler), watch, output, permission, host, settings, clock, scope)

    private fun TestScope.started(): RunTracker {
        val tracker = tracker(backgroundScope)
        assertIs<Outcome.Success<Unit>>(tracker.startRun(freeSpec()))
        runCurrent()
        return tracker
    }

    private fun RunTracker.runFor(seconds: Long) {
        for (t in 1..seconds) onLocationSample(sample(t, t * 3.0))
        clock.advanceSeconds(seconds)
    }

    @Test
    fun `OS 위치 권한이 없으면 consentRequired를 참으로 두고 거부한다`() = runTest {
        permission.granted = false
        val tracker = tracker(backgroundScope)
        assertEquals(
            Outcome.Failure(AppError.Rejected(AppError.Rejected.LOCATION_CONSENT)),
            tracker.checkStartable()
        )
        assertTrue(tracker.consentRequired.value)
    }

    @Test
    fun `서버 위치 동의가 없으면 startRun도 거부하고 측정을 시작하지 않는다`() = runTest {
        settings.snapshot = settings.snapshot.copy(locationConsented = false)
        val tracker = tracker(backgroundScope)
        assertIs<Outcome.Failure>(tracker.startRun(freeSpec()))
        assertTrue(tracker.consentRequired.value)
        assertEquals(0, host.started)
    }

    @Test
    fun `동의와 권한이 있으면 시작하고 측정 서비스를 띄운다`() = runTest {
        val tracker = tracker(backgroundScope)
        assertEquals(Outcome.Success(Unit), tracker.checkStartable())
        assertFalse(tracker.consentRequired.value)
        assertEquals(Outcome.Success(Unit), tracker.startRun(freeSpec()))
        assertEquals(1, host.started)
        assertEquals(
            Outcome.Failure(AppError.Rejected(AppError.Rejected.RUN_IN_PROGRESS)),
            tracker.startRun(freeSpec())
        )
    }

    @Test
    fun `종료 길게 누름 - save 뒤 requestUpload 순서`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        assertIs<FinishOutcome.Saved>(tracker.onFinishLongPressed())
        runCurrent()
        assertEquals(listOf("pending.save", "upload.requestUpload"), log.calls)
        assertEquals(1, host.stopped)
        assertEquals(Distance(27), pending.runs.single().distance)
    }

    @Test
    fun `저장에 실패하면 두 번 더 시도하고, 그래도 실패하면 전송 대신 실패 안내를 낸다`() = runTest {
        pending.failWith = AppError.Storage
        val tracker = started()
        tracker.runFor(10)
        tracker.onFinishLongPressed()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("pending.save", "pending.save", "pending.save"), log.calls)
        assertEquals(0, scheduler.requests)
        assertEquals(Guidance.Kind.SAVE_FAILED, output.played.last().kind)
    }

    @Test
    fun `잠금 중 종료 길게 누름은 거부하고 해제 안내를 낸다`() = runTest {
        val tracker = started()
        tracker.runFor(5)
        tracker.lockScreen()
        assertTrue(tracker.progress.value.locked)
        assertEquals(FinishOutcome.Rejected, tracker.onFinishLongPressed())
        runCurrent()
        assertTrue(log.calls.isEmpty())
        assertEquals(Guidance.Kind.UNLOCK_HINT, output.played.last().kind)
    }

    @Test
    fun `짧게 누름은 안내만, 해제 길게 누름은 해제`() = runTest {
        val tracker = started()
        tracker.onFinishTapped()
        assertEquals(Guidance.Kind.FINISH_HINT, output.played.last().kind)
        tracker.lockScreen()
        tracker.onUnlockTapped()
        assertEquals(Guidance.Kind.UNLOCK_HINT, output.played.last().kind)
        assertTrue(tracker.progress.value.locked)
        tracker.onUnlockLongPressed()
        assertFalse(tracker.progress.value.locked)
    }

    @Test
    fun `조정자의 endRun은 잠금과 무관하게 저장하고 전송을 요청한다`() = runTest {
        val tracker = started()
        tracker.runFor(5)
        tracker.lockScreen()
        assertIs<FinishOutcome.Saved>(tracker.endRun())
        runCurrent()
        assertEquals(listOf("pending.save", "upload.requestUpload"), log.calls)
    }

    @Test
    fun `1초 tick - 진행 지표 방출, 안내 출력, 워치 표시`() = runTest {
        val tracker = started()
        for (t in 1..334L) tracker.onLocationSample(sample(t, t * 3.0))
        tracker.tick(at(334))
        assertEquals(Distance(999), tracker.progress.value.distance)
        tracker.onLocationSample(sample(335, 1_005.0))
        tracker.tick(at(335))
        assertEquals(Guidance.Kind.SPLIT, output.played.single().kind)
        val display = watch.published.last()
        assertTrue(display.isRunning)
        assertEquals(Distance(1_002), display.distance)
    }

    @Test
    fun `격차·순위를 RunSession에 넘겨 안내 입력으로 쓴다`() = runTest {
        val tracker = started()
        tracker.onGapChanged(40)
        tracker.onRankChanged(CrewRank(1, 3))
        tracker.runFor(1)
        assertEquals(FinishOutcome.NotSaved, tracker.endRun())
    }

    @Test
    fun `측정 중 심박은 기록에 담고, 측정 뒤 늦게 온 심박은 appendHeartRates로 넘긴다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        watch.heartRates.emit(listOf(HeartRateSample(at(5), 140)))
        runCurrent()
        tracker.onFinishLongPressed()
        runCurrent()
        assertEquals(listOf(HeartRateSample(at(5), 140)), pending.runs.single().heartRates)

        watch.heartRates.emit(listOf(HeartRateSample(at(8), 150), HeartRateSample(at(500), 99)))
        runCurrent()
        assertEquals(listOf("rec-1" to listOf(HeartRateSample(at(8), 150))), pending.appended)
    }

    @Test
    fun `워치 시작 요청 - 러닝 화면 구독자가 없으면 버리고 안내를 보낸다`() = runTest {
        val tracker = tracker(backgroundScope)
        runCurrent()
        watch.startRequests.emit(Unit)
        runCurrent()
        assertNotNull(watch.published.last().notice)
        assertFalse(tracker.watchStartRequests.replayCache.isNotEmpty())
    }

    @Test
    fun `워치 시작 요청 - 구독자가 있고 측정 중이 아니면 전달한다`() = runTest {
        val tracker = tracker(backgroundScope)
        var received = 0
        backgroundScope.launch { tracker.watchStartRequests.collect { received++ } }
        runCurrent()
        watch.startRequests.emit(Unit)
        runCurrent()
        assertEquals(1, received)
        assertTrue(watch.published.isEmpty())
    }

    @Test
    fun `워치 시작 요청 - 측정 중이면 버리고 안내를 보낸다`() = runTest {
        val tracker = started()
        var received = 0
        backgroundScope.launch { tracker.watchStartRequests.collect { received++ } }
        runCurrent()
        watch.startRequests.emit(Unit)
        runCurrent()
        assertEquals(0, received)
        assertNotNull(watch.published.last().notice)
    }

    @Test
    fun `F-4 늦은 심박 저장이 실패하면 다시 시도하고 남겨 두었다가 다음 묶음과 함께 넣는다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        tracker.onFinishLongPressed()
        runCurrent()
        pending.appendFailWith = AppError.Storage
        watch.heartRates.emit(listOf(HeartRateSample(at(8), 150)))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, log.calls.count { it == "pending.appendHeartRates:rec-1" })
        assertTrue(pending.appended.isEmpty())

        pending.appendFailWith = null
        watch.heartRates.emit(listOf(HeartRateSample(at(9), 151)))
        runCurrent()
        assertEquals(
            listOf("rec-1" to listOf(HeartRateSample(at(8), 150), HeartRateSample(at(9), 151))),
            pending.appended
        )
    }

    @Test
    fun `F-4 기록을 찾을 수 없다는 거부는 다시 시도하지 않는다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        tracker.onFinishLongPressed()
        runCurrent()
        pending.appendFailWith = AppError.Rejected("RECORD_NOT_FOUND")
        watch.heartRates.emit(listOf(HeartRateSample(at(8), 150)))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, log.calls.count { it == "pending.appendHeartRates:rec-1" })
    }

    @Test
    fun `S-18 측정이 끝나면 워치에 러닝 종료 표시를 보낸다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        tracker.onFinishLongPressed()
        runCurrent()
        assertFalse(watch.published.last().isRunning)
    }

    @Test
    fun `워치 시작 화면 - 오늘 계획과 함께 러닝 아님 표시를 보내고 측정이 끝나도 그 계획을 보인다`() = runTest {
        val plan = PlanSnapshot(Distance(5_000), Pace(360))
        val tracker = tracker(backgroundScope)
        tracker.publishWatchHome(plan)
        val home = watch.published.last()
        assertFalse(home.isRunning)
        assertEquals(plan, home.todayPlan)

        assertIs<Outcome.Success<Unit>>(tracker.startRun(freeSpec()))
        tracker.runFor(10)
        tracker.onFinishLongPressed()
        runCurrent()
        assertEquals(plan, watch.published.last().todayPlan)
        assertFalse(watch.published.last().isRunning)
    }

    @Test
    fun `워치 종료 요청 - 자유런은 길게 누르기 종료 규칙으로 끝낸다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        watch.endRequests.emit(Unit)
        runCurrent()
        assertEquals(listOf("pending.save", "upload.requestUpload"), log.calls)
    }

    @Test
    fun `워치 종료 요청 - 잠금 중이면 거부하고 해제 안내를 낸다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        tracker.lockScreen()
        watch.endRequests.emit(Unit)
        runCurrent()
        assertTrue(log.calls.isEmpty())
        assertEquals(Guidance.Kind.UNLOCK_HINT, output.played.last().kind)
    }

    @Test
    fun `워치 종료 요청 - 대결·크루런은 끝내지 않고 워치에 안내한다`() = runTest {
        val tracker = tracker(backgroundScope)
        tracker.startRun(
            RunStartSpec(mode = RunMode.BATTLE, cardKind = CardKind.BATTLE_RUN, battleId = 3, recordId = "b-1")
        )
        runCurrent()
        tracker.runFor(10)
        watch.endRequests.emit(Unit)
        runCurrent()
        assertTrue(log.calls.isEmpty())
        assertNotNull(watch.published.last().notice)
        assertTrue(watch.published.last().isRunning)
    }

    @Test
    fun `측정 서비스를 띄우지 못하면 시작을 되돌리고 거부한다`() = runTest {
        host.startFailure = AppError.Rejected("MEASUREMENT_UNAVAILABLE")
        val tracker = tracker(backgroundScope)
        assertEquals(
            Outcome.Failure(AppError.Rejected("MEASUREMENT_UNAVAILABLE")),
            tracker.startRun(freeSpec())
        )
        host.startFailure = null
        assertIs<Outcome.Success<Unit>>(tracker.startRun(freeSpec()), "앞 시작이 세션을 남기지 않는다")
    }
}
