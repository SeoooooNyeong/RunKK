package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * 자유런 목표 규칙(FR-048·FR-049·FR-044): 코치 계획 따르기가 켜져 있고 오늘 계획이 있으면 계획,
 * 그 밖에는 자유런 설정의 목표 거리(자유면 목표 없음).
 */
class FreeRunGoalTest {
    private val plan = PlanSnapshot(Distance(5_000), Pace(330))
    private val km3 = GuidanceSettings.DEFAULT.copy(freeRunTarget = Distance(3_000))

    @Test
    fun `계획이 없고 설정 목표가 3km면 목표 3km`() {
        val goal = FreeRunGoal.of(null, km3)
        assertEquals(PlanSnapshot(Distance(3_000), null), goal.plan)
        assertFalse(goal.isFromCoachPlan)
    }

    @Test
    fun `코치 계획 따르기 켬 + 오늘 계획 있음이면 계획의 거리·페이스`() {
        val goal = FreeRunGoal.of(plan, km3.copy(isFollowingCoachPlan = true))
        assertEquals(plan, goal.plan)
        assertTrue(goal.isFromCoachPlan)
    }

    @Test
    fun `코치 계획 따르기 꺼짐이면 오늘 계획이 있어도 설정 목표(페이스 없음)`() {
        val goal = FreeRunGoal.of(plan, km3.copy(isFollowingCoachPlan = false))
        assertEquals(PlanSnapshot(Distance(3_000), null), goal.plan)
        assertFalse(goal.isFromCoachPlan)
    }

    @Test
    fun `코치 계획 따르기 켬인데 오늘 계획이 없으면 설정 목표`() {
        val on = km3.copy(isFollowingCoachPlan = true)
        assertEquals(FreeRunGoal(PlanSnapshot(Distance(3_000), null), false), FreeRunGoal.of(null, on))
        assertEquals(
            FreeRunGoal(PlanSnapshot(Distance(3_000), null), false),
            FreeRunGoal.of(PlanSnapshot(null, null), on)
        )
    }

    @Test
    fun `계획에 페이스만 있으면 페이스는 계획, 거리는 설정 목표`() {
        val goal = FreeRunGoal.of(PlanSnapshot(null, Pace(300)), km3.copy(isFollowingCoachPlan = true))
        assertEquals(PlanSnapshot(Distance(3_000), Pace(300)), goal.plan)
        assertTrue(goal.isFromCoachPlan)
    }

    @Test
    fun `설정 목표가 자유이고 계획을 따르지 않으면 목표 없음`() {
        assertEquals(FreeRunGoal.NONE, FreeRunGoal.of(plan, GuidanceSettings.DEFAULT))
        assertNull(FreeRunGoal.of(null, GuidanceSettings.DEFAULT).plan)
    }

    @Test
    fun `목표 도달 안내는 설정 목표 3km에서 울린다(FR-049)`() {
        val goal = FreeRunGoal.of(null, km3).plan
        val judge = CoachingJudge.forMode(com.a207.runkk.app.core.model.RunMode.FREE)
        assertTrue(judge.evaluate(context(progress(2_999), goal)).none { it.kind == Guidance.Kind.TARGET_REACHED })
        assertEquals(
            listOf(Guidance.Kind.TARGET_REACHED),
            judge.evaluate(context(progress(3_000), goal)).map { it.kind }.filter { it == Guidance.Kind.TARGET_REACHED }
        )
    }

    @Test
    fun `유스케이스는 지금 설정 사본으로 목표를 고른다`() {
        val settings = FakeSettingsRepository(SettingsSnapshot.SAFE.copy(guidanceSettings = km3))
        val goals = FreeRunGoalUseCase(settings)
        assertEquals(PlanSnapshot(Distance(3_000), null), goals.current(plan).plan)
        settings.snapshot = settings.snapshot.copy(guidanceSettings = km3.copy(isFollowingCoachPlan = true))
        assertEquals(plan, goals.current(plan).plan)
    }

    @Test
    fun `설정 사본이 바뀌면 목표를 다시 낸다`() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeSettingsRepository(SettingsSnapshot.SAFE)
        val todayPlan = MutableStateFlow<PlanSnapshot?>(null)
        val seen = mutableListOf<FreeRunGoal>()
        val job = FreeRunGoalUseCase(settings).observe(todayPlan).onEach { seen += it }.launchIn(backgroundScope)
        assertEquals(listOf(FreeRunGoal.NONE), seen)
        settings.snapshot = settings.snapshot.copy(guidanceSettings = km3)
        assertEquals(FreeRunGoal(PlanSnapshot(Distance(3_000), null), false), seen.last())
        todayPlan.value = plan
        settings.snapshot = settings.snapshot.copy(guidanceSettings = km3.copy(isFollowingCoachPlan = true))
        assertEquals(FreeRunGoal(plan, true), seen.last())
        assertEquals(FreeRunGoal(plan, true), FreeRunGoalUseCase(settings).observe(todayPlan).first())
        job.cancel()
    }
}
