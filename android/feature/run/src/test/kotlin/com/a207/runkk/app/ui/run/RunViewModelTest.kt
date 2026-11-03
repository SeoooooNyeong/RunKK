package com.a207.runkk.app.ui.run

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.run.FreeRunGoalUseCase
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceMessage
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.MeasurementRuntime
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunCoach
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.RunWatchBridge
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.GuidanceSettings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** RunViewModel: 카운트다운 → startRun, 누름 종류별 연산(D-49), 재동의, 워치 시작 요청. */
@OptIn(ExperimentalCoroutinesApi::class)
class RunViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val pending = FakePendingRunRepository()
    private val upload = FakeUploadScheduler()
    private val watch = FakeWatchGateway()
    private val guidance = FakeGuidanceOutput()
    private val permission = FakeLocationPermission()
    private val host = FakeMeasurementHost()
    private val settings = FakeSettingsRepository()
    private val records = FakeRecordRepository()
    private val clock = MutableClock()
    private val goals = FreeRunGoalUseCase(settings)
    private val km3 = GuidanceSettings.DEFAULT.copy(freeRunTarget = Distance(3_000))

    private fun TestScope.tracker() = RunTracker(
        RunArchive(pending, upload, backgroundScope),
        RunWatchBridge(watch, backgroundScope),
        RunCoach(guidance, settings),
        MeasurementRuntime(permission, host, settings, clock)
    )

    private fun TestScope.started(): Pair<RunViewModel, RunTracker> {
        val tracker = tracker()
        val vm = RunViewModel(tracker, records, goals)
        runCurrent()
        advanceUntilIdle()
        return vm to tracker
    }

    /** 앞선 정상 포인트 다음에 [latitude] 위치 포인트를 넣는다. 이웃 포인트 사이가 25km/h를 넘으면 거리에서 빠지므로(FR-047) 달리기 속도로 둔다. */
    private fun RunTracker.walk(seconds: Long, latitude: Double) {
        clock.now = start.plusSeconds(seconds)
        onLocationSample(LocationSample(clock.now, GeoPoint(latitude, 127.0), accuracyMeters = 5.0))
    }

    @Test
    fun `코치 계획 따르기가 켜져 있으면 카운트다운이 끝난 뒤 오늘 계획으로 자유런을 시작한다`() = runTest(main.dispatcher) {
        settings.guidance = km3.copy(isFollowingCoachPlan = true)
        val tracker = tracker()
        val vm = RunViewModel(tracker, records, goals)
        runCurrent()
        assertEquals(RunPhase.COUNTDOWN, vm.state.value.phase)
        assertEquals(3, vm.state.value.countdown)
        advanceUntilIdle()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
        assertEquals(Distance(5_000), vm.state.value.targetDistance)
        assertEquals(Pace(330), vm.state.value.targetPace)
        assertEquals(1, host.started)
    }

    @Test
    fun `워치가 측정 중이면 카운트다운에 들어가지 않고 워치에서 러닝 중이라고 알린다(FR-252, D-373)`() = runTest(main.dispatcher) {
        watch.isMeasuringOnWatch = true
        val (vm, _) = started()

        assertEquals(RunPhase.PREPARING, vm.state.value.phase)
        assertEquals(RunkkErrorText.rejected(AppError.Rejected.WATCH_MEASURING), vm.state.value.error)
        assertEquals(0, host.started)

        // 워치 측정이 끝난 뒤 다시 시도하면 카운트다운으로 간다.
        watch.isMeasuringOnWatch = false
        vm.onRetryStart()
        assertEquals(RunPhase.COUNTDOWN, vm.state.value.phase)
    }

    @Test
    fun `오늘 계획이 없으면 자유런 설정의 목표 거리 3km로 시작한다(FR-048)`() = runTest(main.dispatcher) {
        records.todayPlanResult = Outcome.Success(null)
        settings.guidance = km3
        val (vm, _) = started()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
        assertEquals(Distance(3_000), vm.state.value.targetDistance)
        assertEquals(null, vm.state.value.targetPace)
    }

    @Test
    fun `코치 계획 따르기가 꺼져 있으면 오늘 계획이 있어도 설정 목표로 시작한다`() = runTest(main.dispatcher) {
        records.todayPlanResult = Outcome.Success(TodayPlan(Distance(5_000), Pace(330)))
        settings.guidance = km3.copy(isFollowingCoachPlan = false)
        val (vm, _) = started()
        assertEquals(Distance(3_000), vm.state.value.targetDistance)
        assertEquals(null, vm.state.value.targetPace)
    }

    @Test
    fun `설정 목표 거리에 닿으면 목표 도달 안내가 울린다(FR-049)`() = runTest(main.dispatcher) {
        records.todayPlanResult = Outcome.Success(null)
        settings.guidance = km3
        val (_, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(1_000, 37.528)
        tracker.tick(clock.now)
        assertTrue(guidance.played.any { it.kind == Guidance.Kind.TARGET_REACHED })
    }

    @Test
    fun `TC-9727 카운트다운 요약은 목표·음성·진동·코치 설정을 담는다`() = runTest(main.dispatcher) {
        records.todayPlanResult = Outcome.Success(null)
        settings.guidance = GuidanceSettings.DEFAULT.copy(freeRunTarget = Distance(5_000), isRunVibrationOn = false)
        val vm = RunViewModel(tracker(), records, goals)
        runCurrent()
        val s = vm.state.value
        assertEquals(RunPhase.COUNTDOWN, s.phase)
        assertEquals(Distance(5_000), s.targetDistance)
        assertEquals(true to false, s.isVoiceOn to s.isVibrationOn)
        assertEquals(false, s.isFromCoachPlan)
    }

    @Test
    fun `숫자를 누르면 바로 시작한다`() = runTest(main.dispatcher) {
        val vm = RunViewModel(tracker(), records, goals)
        runCurrent()
        vm.onCountdownTapped()
        runCurrent()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
    }

    @Test
    fun `카운트다운 중 취소하면 시작하지 않고 나간다`() = runTest(main.dispatcher) {
        val vm = RunViewModel(tracker(), records, goals)
        runCurrent()
        vm.events.test {
            vm.onCancelCountdown()
            assertEquals(RunEvent.Exit, awaitItem())
        }
        advanceUntilIdle()
        assertEquals(0, host.started)
    }

    @Test
    fun `위치 동의가 없으면 카운트다운 전에 재동의를 보인다(FR-031)`() = runTest(main.dispatcher) {
        settings.consented = false
        val vm = RunViewModel(tracker(), records, goals)
        advanceUntilIdle()
        assertEquals(RunPhase.PREPARING, vm.state.value.phase)
        assertTrue(vm.state.value.isConsentRequired)
        assertEquals(0, host.started)
        assertTrue(vm.state.value.isConsentDialogVisible)
        vm.onConsentDialogDismissed()
        assertEquals(false, vm.state.value.isConsentDialogVisible)
        vm.onRetryStart()
        advanceUntilIdle()
        assertTrue(vm.state.value.isConsentDialogVisible)
        settings.consented = true
        vm.onRetryStart()
        advanceUntilIdle()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
    }

    @Test
    fun `종료 짧게 누름은 안내만 하고 길게 누름은 저장 뒤 결과로 간다(FR-181)`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(120, 37.5045)
        tracker.tick(clock.now)
        vm.onFinishTapped()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
        assertEquals(UiText.Res(R.string.run_hold_to_finish), vm.state.value.hint)
        assertTrue(guidance.played.any { it.kind == Guidance.Kind.FINISH_HINT })
        vm.events.test {
            vm.onFinishLongPressed()
            val event = awaitItem()
            assertTrue(event is RunEvent.Finished)
        }
        advanceUntilIdle()
        assertEquals(1, pending.runs.value.size)
        assertEquals(1, upload.requests)
    }

    @Test
    fun `거리 0으로 끝내면 저장하지 않았다고 알린다`() = runTest(main.dispatcher) {
        val (vm, _) = started()
        vm.events.test {
            vm.onFinishLongPressed()
            assertEquals(RunEvent.NotSaved, awaitItem())
        }
    }

    @Test
    fun `1km를 넘을 때 구간 배너를 보인다`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(330, 37.509)
        tracker.tick(clock.now)
        runCurrent()
        val banner = vm.state.value.splitBanner as UiText.Res
        assertEquals(R.string.run_split_banner, banner.id)
        assertEquals(1L, banner.args.first())
    }

    @Test
    fun `측정 중 다시 열면 시작 대신 측정 화면으로 이어진다`() = runTest(main.dispatcher) {
        val tracker = tracker()
        tracker.startRun(
            RunStartSpec(
                mode = com.a207.runkk.app.core.model.RunMode.FREE,
                cardKind = com.a207.runkk.app.core.model.CardKind.FREE_RUN
            )
        )
        val vm = RunViewModel(tracker, records, goals)
        runCurrent()
        vm.onCountdownTapped()
        runCurrent()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
        assertEquals(1, host.started)
    }

    @Test
    fun `저장 뒤 종료를 또 길게 눌러도 결과 이동을 덮지 않는다`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(120, 37.5045)
        vm.events.test {
            vm.onFinishLongPressed()
            vm.onFinishLongPressed()
            assertTrue(awaitItem() is RunEvent.Finished)
            expectNoEvents()
        }
    }

    // ---------- 일시정지·재개(FR-194), 정지 중 지표(FR-034, D-144), 잠금(D-120, D-157), 자동 종료(FR-195) ----------

    @Test
    fun `자유런 측정 중 일시정지하면 정지로 보이고 재개하면 다시 측정한다(FR-194)`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(60, 37.5027)
        tracker.tick(clock.now)
        runCurrent()
        vm.onPauseTapped()
        runCurrent()
        assertTrue(vm.state.value.isPaused)
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
        assertTrue(guidance.played.any { it.message == GuidanceMessage.PAUSED })
        vm.onResumeTapped()
        runCurrent()
        assertFalse(vm.state.value.isPaused)
        assertTrue(guidance.played.any { it.message == GuidanceMessage.RESUMED })
    }

    @Test
    fun `정지 중 현재 페이스는 비고 거리·시간은 늘지 않으며 심박은 갱신한다(D-144)`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(60, 37.5027)
        tracker.tick(clock.now)
        vm.onPauseTapped()
        runCurrent()
        val paused = vm.state.value.progress
        watch.heartRates.emit(listOf(HeartRateSample(clock.now, 141)))
        clock.now = clock.now.plusSeconds(20)
        tracker.tick(clock.now)
        runCurrent()
        val later = vm.state.value.progress
        assertEquals(null, later.currentPace)
        assertEquals(paused.distance, later.distance)
        assertEquals(paused.elapsed, later.elapsed)
        assertEquals(141, later.heartRate?.bpm)
    }

    @Test
    fun `측정 중 경로선은 RunTracker 경로를 따른다(D-261)`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(120, 37.5045)
        runCurrent()
        assertEquals(2, vm.state.value.route.points.size)
        assertEquals(tracker.routeLine.value, vm.state.value.route)
    }

    @Test
    fun `정지 중에도 길게 눌러 끝내면 저장하고 결과로 간다(FR-036)`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(120, 37.5045)
        vm.onPauseTapped()
        runCurrent()
        vm.events.test {
            vm.onFinishLongPressed()
            assertTrue(awaitItem() is RunEvent.Finished)
            expectNoEvents()
        }
        assertEquals(RunPhase.FINISHING, vm.state.value.phase)
    }

    @Test
    fun `워치에서 끝내도 러닝 화면이 결과로 간다`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(120, 37.5045)
        vm.events.test {
            watch.endRequests.emit(Unit)
            runCurrent()
            assertTrue(awaitItem() is RunEvent.Finished)
        }
    }

    @Test
    fun `러닝 화면이 없을 때 워치로 끝났으면 다시 붙은 화면이 카운트다운 대신 결과로 간다(A4)`() = runTest(main.dispatcher) {
        val tracker = tracker()
        tracker.startRun(
            RunStartSpec(
                mode = com.a207.runkk.app.core.model.RunMode.FREE,
                cardKind = com.a207.runkk.app.core.model.CardKind.FREE_RUN
            )
        )
        runCurrent()
        tracker.walk(1, 37.5)
        tracker.walk(120, 37.5045)
        watch.endRequests.emit(Unit)
        runCurrent()
        val vm = RunViewModel(tracker, records, goals)
        vm.events.test {
            runCurrent()
            assertTrue(awaitItem() is RunEvent.Finished)
        }
        advanceUntilIdle()
        assertEquals(RunPhase.FINISHING, vm.state.value.phase, "카운트다운을 다시 시작했다")
        assertEquals(1, host.started)
    }
}
