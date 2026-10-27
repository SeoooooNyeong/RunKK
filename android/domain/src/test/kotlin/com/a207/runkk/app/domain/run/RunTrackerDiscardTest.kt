package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Outcome
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 자유런 취소(기록을 남기지 않는 종료): RunSession.discard와 RunTracker.discardRun. 취소하면 측정 서비스를 멈추고 워치를 시작 화면으로
 * 돌리며, 기록 보관함(PendingRun)·전송 요청·늦은 심박 덧붙이기·남긴 결과(takeUnclaimedEnd) 어디에도 남기지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunTrackerDiscardTest {
    private val log = CallLog()
    private val pending = FakePendingRunRepository(log)
    private val scheduler = FakeUploadScheduler(log)
    private val watch = FakeWatchGateway()
    private val output = FakeGuidanceOutput()
    private val host = FakeMeasurementHost()
    private val clock = TestClock()

    private fun TestScope.tracker(): RunTracker = runTracker(
        pending,
        scheduler,
        watch,
        output,
        FakeLocationPermission(),
        host,
        FakeSettingsRepository(),
        clock,
        backgroundScope
    )

    private fun TestScope.started(spec: RunStartSpec = freeSpec()): RunTracker {
        val tracker = tracker()
        assertIs<Outcome.Success<Unit>>(tracker.startRun(spec))
        runCurrent()
        return tracker
    }

    private fun RunTracker.runFor(seconds: Long) {
        for (t in 1..seconds) onLocationSample(sample(t, t * 3.0))
        clock.advanceSeconds(seconds)
    }

    private fun session(mode: RunMode = RunMode.FREE): RunSession = RunSession(
        RunStartSpec(mode = mode, cardKind = CardKind.FREE_RUN, recordId = "rec-1")
    ).also { it.start(T0) }

    // ---------- RunSession.discard ----------

    @Test
    fun `자유런 RUNNING과 PAUSED에서 discard하면 FINISHED가 되고 측정 중이 아니다`() {
        val running = session()
        assertTrue(running.discard())
        assertEquals(RunSession.State.FINISHED, running.state)
        assertFalse(running.isActive)

        val paused = session().also { it.pause(at(10)) }
        assertTrue(paused.discard())
        assertEquals(RunSession.State.FINISHED, paused.state)
        assertFalse(paused.isPaused)
        assertNull(paused.currentPause(at(20)))
    }

    @Test
    fun `잠금 중(LOCKED, PAUSED_LOCKED)이면 discard는 거짓이고 상태가 그대로다`() {
        val locked = session().also { it.lock() }
        assertFalse(locked.discard())
        assertEquals(RunSession.State.LOCKED, locked.state)

        val pausedLocked = session().also {
            it.pause(at(10))
            it.lock()
        }
        assertFalse(pausedLocked.discard())
        assertEquals(RunSession.State.PAUSED_LOCKED, pausedLocked.state)
    }

    @Test
    fun `대결(셀프·셀럽 대결 포함)·크루런은 discard가 거짓이고 측정을 이어 간다`() {
        for (mode in RunMode.entries.filter { it != RunMode.FREE }) {
            val s = session(mode)
            assertFalse(s.discard(), "$mode")
            assertEquals(RunSession.State.RUNNING, s.state)
        }
    }

    @Test
    fun `시작 전과 끝난 뒤의 discard는 거짓이다`() {
        val notStarted = RunSession(freeSpec())
        assertFalse(notStarted.discard())
        assertEquals(RunSession.State.COUNTDOWN, notStarted.state)

        val finished = session().also { it.end(at(5)) }
        assertFalse(finished.discard())
    }

    // ---------- RunTracker.discardRun ----------

    @Test
    fun `자유런을 취소하면 저장·전송 없이 측정 서비스를 멈추고 워치를 시작 화면으로 돌린다`() = runTest {
        val tracker = started()
        tracker.runFor(30)
        tracker.tick(clock.now)
        assertTrue(watch.published.last().isRunning)

        assertTrue(tracker.discardRun())
        runCurrent()

        assertTrue(log.calls.isEmpty(), "보관함 저장·전송 요청을 부르지 않는다: ${log.calls}")
        assertTrue(pending.runs.isEmpty())
        assertEquals(0, scheduler.requests)
        assertEquals(1, host.stopped)
        assertFalse(watch.published.last().isRunning, "워치 측정도 끝난다(시작 화면 표시)")
        assertNull(watch.published.last().notice)
        assertTrue(output.played.none { it.kind == Guidance.Kind.SAVE_FAILED })
    }

    @Test
    fun `취소는 ended로 저장 안 함을 보내 러닝 화면이 결과 없이 홈으로 가게 하고 남긴 결과를 만들지 않는다`() = runTest {
        val tracker = started()
        tracker.runFor(30)
        val received = mutableListOf<FinishOutcome>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { tracker.ended.collect { received += it } }
        assertTrue(tracker.discardRun())
        assertEquals(listOf<FinishOutcome>(FinishOutcome.NotSaved), received)
        job.cancel()

        // 러닝 화면이 떨어져 있을 때 취소돼도 다시 붙은 화면이 결과로 가지 않는다.
        val other = started()
        other.runFor(30)
        assertTrue(other.discardRun())
        assertNull(other.takeUnclaimedEnd())
    }

    @Test
    fun `정지 중에도 취소할 수 있고 정지 한도 자동 종료가 뒤에 저장하지 않는다`() = runTest {
        val tracker = started()
        tracker.runFor(30)
        tracker.onPauseTapped()
        assertTrue(tracker.progress.value.isPaused)
        assertTrue(tracker.discardRun())
        clock.advanceSeconds(PauseLimit.END_AFTER.seconds + 1)
        tracker.tick(clock.now)
        runCurrent()
        assertTrue(log.calls.isEmpty())
        assertEquals(1, host.stopped)
    }

    @Test
    fun `잠금 중에는 취소를 받지 않고 측정을 이어 간다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        tracker.lockScreen()
        assertFalse(tracker.discardRun())
        assertEquals(0, host.stopped)
        assertTrue(watch.published.isEmpty() || watch.published.last().isRunning)
        assertIs<FinishOutcome.Rejected>(tracker.onFinishLongPressed())
    }

    @Test
    fun `대결·크루런은 취소하지 않는다`() = runTest {
        val tracker = started(
            RunStartSpec(mode = RunMode.BATTLE, cardKind = CardKind.BATTLE_RUN, battleId = 3, recordId = "b-1")
        )
        tracker.runFor(10)
        assertFalse(tracker.discardRun())
        assertEquals(0, host.stopped)
        assertIs<FinishOutcome.Saved>(tracker.endRun())
    }

    @Test
    fun `측정 중이 아니면 취소는 거짓이고 아무것도 부르지 않는다`() = runTest {
        val tracker = tracker()
        runCurrent()
        assertFalse(tracker.discardRun())
        assertEquals(0, host.stopped)
        assertTrue(watch.published.isEmpty())
    }

    @Test
    fun `취소 뒤 늦게 온 심박은 어느 기록에도 덧붙이지 않는다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        watch.heartRates.emit(listOf(HeartRateSample(at(5), 140)))
        runCurrent()
        assertTrue(tracker.discardRun())
        watch.heartRates.emit(listOf(HeartRateSample(at(8), 150)))
        runCurrent()
        assertTrue(pending.appended.isEmpty())
        assertTrue(log.calls.isEmpty())
    }

    @Test
    fun `앞 기록을 저장한 뒤 다음 러닝을 취소해도 앞 기록의 늦은 심박은 그대로 덧붙인다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        assertIs<FinishOutcome.Saved>(tracker.onFinishLongPressed())
        runCurrent()
        assertIs<Outcome.Success<Unit>>(tracker.startRun(freeSpec().copy(recordId = "rec-2")))
        tracker.runFor(10)
        assertTrue(tracker.discardRun())
        watch.heartRates.emit(listOf(HeartRateSample(at(8), 150), HeartRateSample(at(15), 151)))
        runCurrent()
        assertEquals(listOf("rec-1" to listOf(HeartRateSample(at(8), 150))), pending.appended)
        assertEquals(listOf("rec-1"), pending.runs.map { it.recordId })
    }

    @Test
    fun `취소한 뒤 새 러닝을 시작할 수 있다`() = runTest {
        val tracker = started()
        tracker.runFor(10)
        assertTrue(tracker.discardRun())
        assertIs<Outcome.Success<Unit>>(tracker.startRun(freeSpec()))
        assertEquals(2, host.started)
        assertTrue(tracker.progress.value.elapsed.isZero)
    }
}
