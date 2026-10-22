package com.a207.runkk.app.ui.run

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.run.FreeRunGoalUseCase
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.settings.GuidanceSettings
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    private fun TestScope.tracker() =
        RunTracker(RunArchive(pending, upload), watch, guidance, permission, host, settings, clock, backgroundScope)

    private fun TestScope.started(): Pair<RunViewModel, RunTracker> {
        val tracker = tracker()
        val vm = RunViewModel(tracker, records, goals)
        runCurrent()
        advanceUntilIdle()
        return vm to tracker
    }

    /** 앞선 정상 포인트 다음에 약 [meters]m 북쪽 포인트를 넣는다. */
    private fun RunTracker.walk(seconds: Long, latitude: Double) {
        clock.now = start.plusSeconds(seconds)
        onLocationSample(LocationSample(clock.now, GeoPoint(latitude, 127.0), accuracyMeters = 5.0))
    }

    @Test
    fun `코치 계획 따르기가 켜져 있으면 카운트다운이 끝난 뒤 오늘 계획으로 자유런을 시작한다`() = runTest(main.dispatcher) {
        settings.guidance = km3.copy(shouldFollowCoachPlan = true)
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
        settings.guidance = km3.copy(shouldFollowCoachPlan = false)
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
    fun `카운트다운 중 설정에서 목표를 바꾸고 돌아오면 바꾼 목표로 다시 센다`() = runTest(main.dispatcher) {
        records.todayPlanResult = Outcome.Success(null)
        val vm = RunViewModel(tracker(), records, goals)
        runCurrent()
        assertEquals(null, vm.state.value.targetDistance)
        vm.onSettingsTapped()
        settings.guidance = GuidanceSettings.DEFAULT.copy(freeRunTarget = Distance(10_000))
        vm.onResumed()
        advanceUntilIdle()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
        assertEquals(Distance(10_000), vm.state.value.targetDistance)
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
        assertTrue(vm.state.value.shouldShowConsentDialog)
        vm.onConsentDialogDismissed()
        assertEquals(false, vm.state.value.shouldShowConsentDialog)
        vm.onRetryStart()
        advanceUntilIdle()
        assertTrue(vm.state.value.shouldShowConsentDialog)
        settings.consented = true
        vm.onRetryStart()
        advanceUntilIdle()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
    }

    @Test
    fun `종료 짧게 누름은 안내만 하고 길게 누름은 저장 뒤 결과로 간다(FR-181)`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(60, 37.5045)
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
    fun `잠금 중에는 해제 짧게 누름이 안내만, 길게 누름이 해제한다(FR-182)`() = runTest(main.dispatcher) {
        val (vm, _) = started()
        vm.onLockTapped()
        runCurrent()
        assertEquals(RunPhase.LOCKED, vm.state.value.phase)
        vm.onUnlockTapped()
        runCurrent()
        assertEquals(RunPhase.LOCKED, vm.state.value.phase)
        assertEquals(UiText.Res(R.string.run_hold_to_unlock), vm.state.value.hint)
        vm.onUnlockLongPressed()
        runCurrent()
        assertEquals(RunPhase.RUNNING, vm.state.value.phase)
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
    fun `카운트다운 중 설정으로 가면 멈추고 돌아오면 다시 센다`() = runTest(main.dispatcher) {
        val vm = RunViewModel(tracker(), records, goals)
        runCurrent()
        vm.onSettingsTapped()
        advanceUntilIdle()
        assertEquals(RunPhase.PREPARING, vm.state.value.phase)
        assertEquals(0, host.started)
        vm.onResumed()
        runCurrent()
        assertEquals(RunPhase.COUNTDOWN, vm.state.value.phase)
    }

    @Test
    fun `저장 뒤 종료를 또 길게 눌러도 결과 이동을 덮지 않는다`() = runTest(main.dispatcher) {
        val (vm, tracker) = started()
        tracker.walk(1, 37.5)
        tracker.walk(60, 37.5045)
        vm.events.test {
            vm.onFinishLongPressed()
            vm.onFinishLongPressed()
            assertTrue(awaitItem() is RunEvent.Finished)
            expectNoEvents()
        }
    }
}
