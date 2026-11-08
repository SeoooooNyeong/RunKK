package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.PlanProgress
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.TodayPlanCache
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

/** 성장 분석·리포트·랭킹·목표·코치 ViewModel(FR-061~FR-075, FR-187). */
class AnalysisViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val todayPlans = TodayPlanCache()

    private companion object {
        /** clock의 날짜(한국 시각 2026-10-21 낮 12시). */
        val TODAY: LocalDate = LocalDate.of(2026, 10, 21)
    }

    @Test
    fun `앱 시계가 UTC여도 오늘·이번 주는 기기 시간대로 셈한다(MR !91 e2e 2 후속)`() {
        // 한국 10/21 00:30 = UTC 10/20 15:30
        val utc = Clock.fixed(Instant.parse("2026-10-20T15:30:00Z"), java.time.ZoneOffset.UTC)
        val vm = CoachViewModel(records, todayPlans, utc, ZoneId.of("Asia/Seoul"))
        assertEquals(TODAY, vm.state.value.today)
        assertEquals(LocalDate.of(2026, 10, 19), vm.state.value.weekStart)
    }

    @Test
    fun `오늘 계획 거리가 없으면 고른 목표 페이스를 두었다가 거리를 고를 때 함께 저장한다(D146)`() {
        val vm = CoachViewModel(records, todayPlans, clock)
        vm.onTodayPace(Pace(240))
        assertFalse(records.calls.any { it.startsWith("plan:") }, "거리가 없으면 아직 보내지 않는다")
        assertEquals(Pace(240), vm.state.value.todayPace)
        vm.onTodayPlan(Distance(5_000))
        assertTrue("plan:2026-10-21=5000:240" in records.calls)
        assertEquals(PlanSnapshot(Distance(5_000), Pace(240)), todayPlans.plan.value)
    }

    @Test
    fun `오늘 계획 거리가 있으면 목표 페이스를 고르는 즉시 저장한다(D146)`() {
        records.extraProgressDays = listOf(PlanProgress.Day(TODAY, Distance(3_000), Distance.ZERO, null))
        val vm = CoachViewModel(records, todayPlans, clock)
        vm.onTodayPace(Pace(300))
        assertTrue("plan:2026-10-21=3000:300" in records.calls)
        assertEquals(PlanSnapshot(Distance(3_000), Pace(300)), todayPlans.plan.value)
    }

    @Test
    fun `목표 페이스를 저장하지 못하면 고르기 전 페이스로 되돌린다(리뷰 AN11-6)`() {
        records.extraProgressDays = listOf(PlanProgress.Day(TODAY, Distance(3_000), Distance.ZERO, null))
        records.todayPlanResult = Outcome.Success(TodayPlan(Distance(3_000), Pace(360)))
        records.planResult = Outcome.Failure(AppError.Rejected("PAST_DATE"))
        val vm = CoachViewModel(records, todayPlans, clock)
        assertEquals(Pace(360), vm.state.value.todayPace, "오늘 계획의 페이스를 읽어 채운다")
        vm.onTodayPace(Pace(300))
        assertEquals(Pace(360), vm.state.value.todayPace)
        assertNull(todayPlans.plan.value)
    }

    @Test
    fun `오늘 계획을 정하면 앱의 오늘 계획 사본도 바꾼다(워치 시작 화면, FR-042)`() {
        val vm = CoachViewModel(records, todayPlans, clock)
        vm.onTodayPlan(Distance(5_000))
        assertEquals(PlanSnapshot(Distance(5_000), null), todayPlans.plan.value)
    }

    @Test
    fun `오늘 계획을 정하지 못하면 앱의 오늘 계획 사본은 그대로 둔다`() {
        records.planResult = Outcome.Failure(AppError.Rejected("PAST_DATE"))
        val vm = CoachViewModel(records, todayPlans, clock)
        vm.onTodayPlan(Distance(5_000))
        assertNull(todayPlans.plan.value)
    }

    @Test
    fun `오늘 계획과 같은 거리를 다시 누르면 오늘 계획을 지우고 사본도 비운다(S15P31A207-275)`() {
        records.extraProgressDays = listOf(PlanProgress.Day(TODAY, Distance(5_000), Distance.ZERO, null))
        todayPlans.put(PlanSnapshot(Distance(5_000), null))
        val vm = CoachViewModel(records, todayPlans, clock)
        vm.onTodayPlan(Distance(5_000))
        assertTrue("deletePlan:2026-10-21" in records.calls)
        assertTrue(records.calls.none { it.startsWith("plan:") }, "같은 거리면 저장하지 않는다")
        assertNull(todayPlans.plan.value)
        assertEquals(UiText.Res(R.string.record_coach_today_plan_cleared), vm.state.value.message)
        assertEquals(2, records.calls.count { it.startsWith("progress:") }, "지운 뒤 다시 읽는다")
    }

    @Test
    fun `오늘 계획과 다른 거리를 누르면 오늘 계획을 바꾼다`() {
        records.extraProgressDays = listOf(PlanProgress.Day(TODAY, Distance(5_000), Distance.ZERO, null))
        val vm = CoachViewModel(records, todayPlans, clock)
        vm.onTodayPlan(Distance(3_000))
        assertTrue("plan:2026-10-21=3000" in records.calls)
        assertTrue(records.calls.none { it.startsWith("deletePlan:") })
        assertEquals(PlanSnapshot(Distance(3_000), null), todayPlans.plan.value)
    }

    @Test
    fun `오늘 계획을 지우지 못하면 사본은 그대로 두고 사유 문구로 알린다`() {
        records.extraProgressDays = listOf(PlanProgress.Day(TODAY, Distance(5_000), Distance.ZERO, null))
        records.deletePlanResult = Outcome.Failure(AppError.Network)
        todayPlans.put(PlanSnapshot(Distance(5_000), null))
        val vm = CoachViewModel(records, todayPlans, clock)
        vm.onTodayPlan(Distance(5_000))
        assertEquals(PlanSnapshot(Distance(5_000), null), todayPlans.plan.value)
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.message)
        assertEquals(1, records.calls.count { it.startsWith("progress:") }, "실패하면 다시 읽지 않는다")
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun `성장 분석은 주간으로 읽고 월간으로 바꾼다`() {
        val vm = GrowthViewModel(records)
        assertEquals(sampleGrowth, vm.state.value.growth)
        vm.onPeriodSelected(AggregationPeriod.MONTHLY)
        assertEquals(listOf("growth:WEEKLY", "growth:MONTHLY"), records.calls)
    }

    @Test
    fun `랭킹은 개인·크루와 기간을 바꿔 읽는다(FR-070·FR-071)`() {
        val vm = RankingViewModel(records, clock)
        vm.onScopeSelected(RankingScope.CREW)
        vm.onPeriodSelected(AggregationPeriod.MONTHLY)
        assertEquals(listOf("personal:WEEKLY", "crew:WEEKLY", "crew:MONTHLY"), records.calls)
        assertEquals(sampleRanking, vm.state.value.ranking)
    }

    @Test
    fun `PL5-A1 기간을 빨리 바꾸면 늦게 온 앞 기간 응답이 고른 기간의 랭킹을 덮지 않는다`() {
        val weekly = sampleRanking.copy(gapToNext = Distance(1_000))
        val monthly = sampleRanking.copy(gapToNext = Distance(9_000))
        records.rankingByPeriod[AggregationPeriod.WEEKLY] = Outcome.Success(weekly)
        records.rankingByPeriod[AggregationPeriod.MONTHLY] = Outcome.Success(monthly)
        val weeklyGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        records.rankingGates[AggregationPeriod.WEEKLY] = weeklyGate
        val vm = RankingViewModel(records, clock) // 주간 요청이 문에 걸려 기다린다
        vm.onPeriodSelected(AggregationPeriod.MONTHLY)
        assertEquals(monthly, vm.state.value.ranking)
        weeklyGate.complete(Unit) // 늦게 온 주간 응답
        assertEquals(AggregationPeriod.MONTHLY, vm.state.value.period)
        assertEquals(monthly, vm.state.value.ranking)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `랭킹 v3 집계 기간과 마감까지 남은 시간은 한국 시각 주(월~일)·달로 센다`() {
        // clock = 한국 시각 2026-10-21(수) 12:00
        val vm = RankingViewModel(records, clock)
        assertEquals(
            RankingWindow(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), Duration.ofHours(4 * 24 + 12)),
            vm.state.value.window
        )
        vm.onPeriodSelected(AggregationPeriod.MONTHLY)
        assertEquals(
            RankingWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), Duration.ofHours(10 * 24 + 12)),
            vm.state.value.window
        )
    }

    @Test
    fun `TC-5857 랭킹 행은 공개 티어 배지를 쓰고 없으면 그리지 않는다`() {
        val placing = Ranking.Entry(1, "바람", Distance(48_000), publicTier = "UNRANKED")
        val gold = Ranking.Entry(2, "구름", Distance(41_000), publicTier = "GOLD")
        val gone = Ranking.Entry(3, "탈퇴", Distance(39_000), publicTier = null)
        records.rankingResult = Outcome.Success(
            sampleRanking.copy(entries = listOf(placing, gold, gone), mine = placing.copy(name = "나"))
        )
        val state = RankingViewModel(records, clock).state.value

        assertEquals(
            listOf(TierDisplay.Placing, TierDisplay.Confirmed("GOLD"), null),
            state.ranking?.entries?.map(::rankingTier)
        )
        // 본인 행도 공개 티어다(임시 티어 없음).
        assertEquals(TierDisplay.Placing, state.ranking?.mine?.let(::rankingTier))
        assertEquals(placing, state.podium?.first)
    }

    @Test
    fun `랭킹 오류는 문구로 보인다`() {
        records.rankingResult = Outcome.Failure(AppError.Network)
        assertEquals(RunkkErrorText.of(AppError.Network), RankingViewModel(records, clock).state.value.error)
    }

    @Test
    fun `코치는 이번 주(월~일) 계획 현황을 읽고 오늘 계획·주간 목표를 정한다(FR-072~FR-075)`() {
        val vm = CoachViewModel(records, todayPlans, clock)
        assertEquals("progress:2026-10-19..2026-10-25", records.calls.first())
        assertNotNull(vm.state.value.progress)
        vm.onTodayPlan(Distance(5_000))
        vm.onGoalRunsSelected(4)
        vm.onGoalDistanceSelected(Distance(20_000))
        vm.onSaveWeeklyGoal()
        assertTrue("plan:2026-10-21=5000" in records.calls)
        assertTrue("weeklyGoal:4:20000" in records.calls)
        assertEquals(UiText.Res(R.string.record_coach_goal_saved, listOf(4, "20")), vm.state.value.message)
        assertEquals(2, records.calls.count { it.startsWith("progress:") })
    }

    @Test
    fun `주간 목표는 횟수와 거리를 모두 골라야 보낸다(서버가 둘 다 필수)`() {
        val vm = CoachViewModel(records, todayPlans, clock)
        vm.onGoalDistanceSelected(Distance(20_000))
        assertEquals(false, vm.state.value.canSaveGoal)
        vm.onSaveWeeklyGoal()
        assertTrue(records.calls.none { it.startsWith("weeklyGoal") })
        vm.onGoalRunsSelected(3)
        assertEquals(true, vm.state.value.canSaveGoal)
    }

    @Test
    fun `주간 목표 범위 오류와 지난 날짜 계획은 사유 문구로 알린다`() {
        records.goalResult = Outcome.Failure(AppError.Rejected("GOAL_INVALID"))
        records.planResult = Outcome.Failure(AppError.Rejected("PAST_DATE"))
        val vm = CoachViewModel(records, todayPlans, clock)
        vm.onGoalRunsSelected(3)
        vm.onGoalDistanceSelected(Distance(20_000))
        vm.onSaveWeeklyGoal()
        assertEquals(RunkkErrorText.rejected("GOAL_INVALID"), vm.state.value.message)
        vm.onMessageShown()
        vm.onTodayPlan(Distance(5_000))
        assertEquals(RunkkErrorText.rejected("PAST_DATE"), vm.state.value.message)
    }
}
