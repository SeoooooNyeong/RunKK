package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.testing.FakeGuidanceOutput
import com.a207.runkk.app.domain.testing.FakeLocationPermission
import com.a207.runkk.app.domain.testing.FakeMeasurementHost
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import com.a207.runkk.app.domain.testing.FakeUploadScheduler
import com.a207.runkk.app.domain.testing.FakeWatchGateway
import com.a207.runkk.app.domain.testing.TestClock
import com.a207.runkk.app.domain.testing.runTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * WatchHomeSync(FR-042, N-21): 앱 시작 때 워치 시작 화면을 한 번 보내고, 오늘 계획이나 자유런 설정이 바뀌어 목표가 바뀔 때마다
 * 다시 보낸다. 실은 계획은 워치에서 시작을 누르면 달릴 자유런 목표(FreeRunGoal.of)다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchHomeSyncTest {
    private val watch = FakeWatchGateway()
    private val settings = FakeSettingsRepository()
    private val todayPlans = TodayPlanCache()
    private val follow = GuidanceSettings.DEFAULT.copy(isFollowingCoachPlan = true)

    private fun TestScope.started(): Pair<WatchHomeSync, RunTracker> {
        val tracker =
            runTracker(
                FakePendingRunRepository(),
                FakeUploadScheduler(),
                watch,
                FakeGuidanceOutput(),
                FakeLocationPermission(),
                FakeMeasurementHost(),
                settings,
                TestClock(),
                backgroundScope
            )
        val sync = WatchHomeSync(tracker, FreeRunGoalUseCase(settings), todayPlans, backgroundScope)
        sync.start()
        runCurrent()
        return sync to tracker
    }

    @Test
    fun `시작하면 오늘 계획을 실은 시작 화면을 한 번 보낸다`() = runTest {
        settings.guidance = follow
        todayPlans.put(PLAN_5K)

        started()

        assertEquals(1, watch.published.size)
        assertFalse(watch.published.single().isRunning)
        assertEquals(PLAN_5K, watch.published.single().todayPlan)
    }

    @Test
    fun `오늘 계획이 바뀌면 시작 화면을 다시 보낸다`() = runTest {
        settings.guidance = follow
        started()

        todayPlans.put(PLAN_5K)
        runCurrent()

        assertEquals(2, watch.published.size)
        assertEquals(PLAN_5K, watch.published.last().todayPlan)
    }

    @Test
    fun `자유런 설정 목표가 바뀌면 시작 화면을 다시 보낸다(FR-048)`() = runTest {
        started()
        assertEquals(null, watch.published.last().todayPlan)

        settings.guidance = GuidanceSettings.DEFAULT.copy(freeRunTarget = Distance(10_000))
        runCurrent()

        assertEquals(2, watch.published.size)
        assertEquals(PlanSnapshot(Distance(10_000), null), watch.published.last().todayPlan)
    }

    @Test
    fun `목표가 그대로면 다시 보내지 않고 start를 두 번 불러도 한 번만 구독한다`() = runTest {
        settings.guidance = follow
        todayPlans.put(PLAN_5K)
        val (sync, _) = started()

        sync.start()
        todayPlans.put(PLAN_5K.copy())
        settings.guidance = follow.copy(isRunVoiceOn = !follow.isRunVoiceOn)
        runCurrent()

        assertEquals(1, watch.published.size)
    }

    @Test
    fun `측정 중에 계획이 바뀌면 진행 표시를 덮지 않고 러닝이 끝난 뒤 시작 화면에 보인다`() = runTest {
        settings.guidance = follow
        val (_, tracker) = started()
        assertIs<Outcome.Success<Unit>>(tracker.startRun(freeSpec()))
        val before = watch.published.size

        todayPlans.put(PLAN_5K)
        runCurrent()
        assertEquals(before, watch.published.size)

        tracker.endRun()
        runCurrent()
        assertFalse(watch.published.last().isRunning)
        assertEquals(PLAN_5K, watch.published.last().todayPlan)
        assertTrue(watch.published.size > before)
    }

    private fun freeSpec() = RunStartSpec(
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        startDevice = RunStartSpec.StartDevice.PHONE,
        todayPlan = null
    )

    private var FakeSettingsRepository.guidance: GuidanceSettings
        get() = snapshot.guidanceSettings
        set(value) {
            snapshot = snapshot.copy(guidanceSettings = value)
        }

    private companion object {
        val PLAN_5K = PlanSnapshot(Distance(5_000), Pace(330))
    }
}
