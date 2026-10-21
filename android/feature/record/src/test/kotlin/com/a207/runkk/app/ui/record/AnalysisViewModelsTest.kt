package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.AggregationPeriod
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

/** 성장 분석·리포트·랭킹·목표·코치 ViewModel(FR-061~FR-075, FR-187). */
class AnalysisViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul"))

    @Test
    fun `성장 분석은 주간으로 읽고 월간으로 바꾼다`() {
        val vm = GrowthViewModel(records)
        assertEquals(sampleGrowth, vm.state.value.growth)
        vm.onPeriodSelected(AggregationPeriod.MONTHLY)
        assertEquals(listOf("growth:WEEKLY", "growth:MONTHLY"), records.calls)
    }

    @Test
    fun `리포트 코멘트가 없으면 준비 중으로 보인다(FR-066)`() {
        val vm = ReportViewModel(records)
        assertEquals("코멘트 준비 중", vm.state.value.commentText)
    }

    @Test
    fun `랭킹은 개인·크루와 기간을 바꿔 읽는다(FR-070·FR-071)`() {
        val vm = RankingViewModel(records)
        vm.onScopeSelected(RankingScope.CREW)
        vm.onPeriodSelected(AggregationPeriod.MONTHLY)
        assertEquals(listOf("personal:WEEKLY", "crew:WEEKLY", "crew:MONTHLY"), records.calls)
        assertEquals(sampleRanking, vm.state.value.ranking)
    }

    @Test
    fun `랭킹 오류는 문구로 보인다`() {
        records.rankingResult = Outcome.Failure(AppError.Network)
        assertTrue(RankingViewModel(records).state.value.error!!.startsWith("네트워크"))
    }

    @Test
    fun `코치는 이번 주(월~일) 계획 현황을 읽고 오늘 계획·주간 목표를 정한다(FR-072~FR-075)`() {
        val vm = CoachViewModel(records, clock)
        assertEquals("progress:2026-10-19..2026-10-25", records.calls.first())
        assertNotNull(vm.state.value.progress)
        vm.onTodayPlan(Distance(5_000))
        vm.onGoalRunsSelected(4)
        vm.onGoalDistanceSelected(Distance(20_000))
        vm.onSaveWeeklyGoal()
        assertTrue("plan:2026-10-21=5000" in records.calls)
        assertTrue("weeklyGoal:4:20000" in records.calls)
        assertEquals("이번 주 목표를 주 4회 · 20km로 정했어요", vm.state.value.message)
        assertEquals(2, records.calls.count { it.startsWith("progress:") })
    }

    @Test
    fun `주간 목표는 횟수와 거리를 모두 골라야 보낸다(서버가 둘 다 필수)`() {
        val vm = CoachViewModel(records, clock)
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
        val vm = CoachViewModel(records, clock)
        vm.onGoalRunsSelected(3)
        vm.onGoalDistanceSelected(Distance(20_000))
        vm.onSaveWeeklyGoal()
        assertEquals("주간 목표는 1~7회와 0km보다 긴 거리로 정해 주세요", vm.state.value.message)
        vm.onMessageShown()
        vm.onTodayPlan(Distance(5_000))
        assertEquals("지난 날짜에는 계획을 넣을 수 없어요", vm.state.value.message)
    }
}
