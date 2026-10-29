package com.a207.runkk.app.ui.run

import app.cash.turbine.test
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.domain.run.FreeRunGoalUseCase
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.MeasurementRuntime
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunCoach
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.RunWatchBridge
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** 자유런 취소(B2·B2-2): 확인 대화상자를 거쳐 기록을 남기지 않고 끝낸 뒤 홈으로 간다. */
@OptIn(ExperimentalCoroutinesApi::class)
class RunDiscardViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val pending = FakePendingRunRepository()
    private val upload = FakeUploadScheduler()
    private val watch = FakeWatchGateway()
    private val host = FakeMeasurementHost()
    private val settings = FakeSettingsRepository()
    private val clock = MutableClock()

    private fun TestScope.started(): Pair<RunViewModel, RunTracker> {
        val tracker = RunTracker(
            RunArchive(pending, upload, backgroundScope),
            RunWatchBridge(watch, backgroundScope),
            RunCoach(FakeGuidanceOutput(), settings),
            MeasurementRuntime(FakeLocationPermission(), host, settings, clock)
        )
        val vm = RunViewModel(tracker, FakeRecordRepository(), FreeRunGoalUseCase(settings), clock)
        runCurrent()
        advanceUntilIdle()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
        return vm to tracker
    }

    /** 약 500m를 1분에 달린다. */
    private fun RunTracker.runHalfKm() {
        clock.now = start.plusSeconds(1)
        onLocationSample(LocationSample(clock.now, GeoPoint(37.5, 127.0), accuracyMeters = 5.0))
        clock.now = start.plusSeconds(60)
        onLocationSample(LocationSample(clock.now, GeoPoint(37.5045, 127.0), accuracyMeters = 5.0))
        tick(clock.now)
    }

    @Test
    fun `취소를 누르면 확인을 묻고 계속 달리기를 고르면 측정을 이어 간다`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.runHalfKm()
        vm.onDiscardTapped()
        assertTrue(vm.state.value.isDiscardDialogVisible)
        vm.events.test {
            vm.onDiscardDismissed()
            runCurrent()
            expectNoEvents()
        }
        assertFalse(vm.state.value.isDiscardDialogVisible)
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
        assertEquals(0, host.stopped)
    }

    @Test
    fun `취소를 확인하면 저장·전송 없이 측정 서비스와 워치 측정을 끝내고 홈으로 간다`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.runHalfKm()
        vm.onDiscardTapped()
        vm.events.test {
            vm.onDiscardConfirmed()
            runCurrent()
            assertEquals(RunEvent.NotSaved, awaitItem())
            expectNoEvents()
        }
        runCurrent()
        assertEquals(RunPhase.FINISHING, vm.state.value.phase)
        assertFalse(vm.state.value.isDiscardDialogVisible)
        assertTrue(pending.runs.value.isEmpty(), "미전송 기록에 남기지 않는다")
        assertEquals(0, upload.requests)
        assertEquals(1, host.stopped)
        assertFalse(watch.published.last().isRunning)
    }

    @Test
    fun `일시정지 중에도 취소를 확인하면 기록 없이 홈으로 간다`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.runHalfKm()
        vm.onPauseTapped()
        runCurrent()
        assertTrue(vm.state.value.isPaused)
        vm.onDiscardTapped()
        assertTrue(vm.state.value.isDiscardDialogVisible)
        vm.events.test {
            vm.onDiscardConfirmed()
            runCurrent()
            assertEquals(RunEvent.NotSaved, awaitItem())
        }
        runCurrent()
        assertTrue(pending.runs.value.isEmpty())
        assertEquals(1, host.stopped)
    }
}
