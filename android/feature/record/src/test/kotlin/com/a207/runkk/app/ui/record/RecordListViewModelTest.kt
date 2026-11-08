package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordList
import com.a207.runkk.app.domain.record.RecordRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test

/** 기록 화면 3탭 ViewModel(D-279, FR-057·FR-058). 오늘은 2026-10-20(화, KST)이고 이번 주는 10-19(월)부터다. */
class RecordListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val pending = FakePendingRunRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val today = LocalDate.of(2026, 10, 20)

    private fun viewModel() = RecordListViewModel(records, pending, clock)

    @Test
    fun `앱 시계가 UTC여도 오늘·달·날짜 묶음은 기기 시간대로 셈한다(MR !91 e2e 2 후속)`() {
        // 한국 2026-11-01 00:30 = UTC 2026-10-31 15:30. 앱 Clock은 Clock.systemUTC()다(DeviceBindings)
        val utc = Clock.fixed(Instant.parse("2026-10-31T15:30:00Z"), java.time.ZoneOffset.UTC)
        val s = RecordListViewModel(records, pending, utc, ZoneId.of("Asia/Seoul")).state.value

        assertEquals(LocalDate.of(2026, 11, 1), s.today)
        assertEquals(YearMonth.of(2026, 11), s.month)
        assertEquals(ZoneId.of("Asia/Seoul"), s.zone)
    }

    @Test
    fun `이번 달 본인 기록을 읽고 최신순으로 둔다(FR-057)`() {
        val s = viewModel().state.value
        assertEquals(YearMonth.of(2026, 10), s.month)
        assertEquals(
            Triple(RecordRepository.SELF, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
            records.runsCalls.single()
        )
        assertEquals(listOf("r3", "r2", "r1"), s.runs.map { it.recordId })
    }

    @Test
    fun `TC-9731 이번 달 월 요약은 서버 monthRuns·monthMeters와 오늘 기준 연속 일수를 그대로 쓴다`() {
        val s = viewModel().state.value
        assertEquals(listOf(today), records.dashboardCalls)
        assertEquals(RecordListUiState.MonthSummary(12, Distance(61_300), 4, 0), s.summary)
    }

    @Test
    fun `TC-9731 지난 달은 그 달 첫날로 월 요약을, 오늘로 연속 일수를 읽는다`() {
        records.dashboardOf = { base ->
            Outcome.Success(
                if (base == today) {
                    dashboard(monthRuns = 12, monthMeters = 61_300, streakDays = 4)
                } else {
                    dashboard(monthRuns = 3, monthMeters = 9_000, streakDays = 0)
                }
            )
        }
        val vm = viewModel()
        records.dashboardCalls.clear()
        vm.onPreviousMonth()
        assertEquals(setOf(LocalDate.of(2026, 9, 1), today), records.dashboardCalls.toSet())
        assertEquals(RecordListUiState.MonthSummary(3, Distance(9_000), 4, 0), vm.state.value.summary)
    }

    @Test
    fun `TC-9743 옛 서버라 월 요약이 없으면 받은 유효 기록으로 세고 대시보드를 못 읽으면 연속 일수는 비운다`() {
        records.dashboardOf = { Outcome.Success(dashboard(monthRuns = null, monthMeters = null, streakDays = 2)) }
        assertEquals(RecordListUiState.MonthSummary(2, Distance(15_220), 2, 0), viewModel().state.value.summary)
        records.dashboardOf = { Outcome.Failure(AppError.Network) }
        assertEquals(RecordListUiState.MonthSummary(2, Distance(15_220), null), viewModel().state.value.summary)
    }

    @Test
    fun `TC-9730 달력 칸 거리는 유효 기록만 더하고 집계 제외 기록만 있는 날은 칸이 비어 있다`() {
        records.runsResult = Outcome.Success(
            RecordList(
                listOf(
                    summary("valid", 26, 3_090),
                    summary("teleport", 26, 9_078_810, isValid = false),
                    summary("only", 25, 9_078_810, isValid = false)
                )
            )
        )
        val s = viewModel().state.value
        assertEquals(mapOf(LocalDate.of(2026, 10, 26) to Distance(3_090)), s.dailyDistance)
    }

    @Test
    fun `TC-9731 날짜를 누르면 그날 기록 줄을 보이고 처음에는 이 달 마지막 기록 날을 고른다`() {
        val vm = viewModel()
        assertEquals(LocalDate.of(2026, 10, 20), vm.state.value.selectedDate)
        assertEquals(listOf("r3"), vm.state.value.dayRuns.map { it.recordId })
        vm.onDateSelected(LocalDate.of(2026, 10, 14))
        // 집계 제외 기록도 그날 줄에는 보인다(상세에서 삭제할 수 있게). 유효 여부는 보이지 않는다.
        assertEquals(listOf("r2", "r1"), vm.state.value.dayRuns.map { it.recordId })
        vm.onDateSelected(LocalDate.of(2026, 10, 2))
        assertEquals(emptyList(), vm.state.value.dayRuns)
    }

    @Test
    fun `TC-9733 목록 탭은 최근 90일 기록을 읽어 이번 주와 이전으로 묶는다`() {
        val vm = viewModel()
        vm.onViewSelected(RecordView.LIST)
        assertEquals(RecordView.LIST, vm.state.value.view)
        assertEquals(Triple(RecordRepository.SELF, today.minusDays(89), today), records.runsCalls.last())
        assertEquals(listOf("r3"), vm.state.value.thisWeekRuns.map { it.recordId })
        assertEquals(listOf("r2", "r1"), vm.state.value.earlierRuns.map { it.recordId })
        vm.onViewSelected(RecordView.CALENDAR)
        vm.onViewSelected(RecordView.LIST)
        assertEquals(2, records.runsCalls.size, "목록은 한 번만 읽는다")
    }

    @Test
    fun `대결 탭으로 열면 처음 한 번만 그 탭을 고른다(D-281)`() {
        val vm = viewModel()
        vm.openView(RecordView.BATTLE)
        assertEquals(RecordView.BATTLE, vm.state.value.view)
        vm.onViewSelected(RecordView.CALENDAR)
        vm.openView(RecordView.BATTLE)
        assertEquals(RecordView.CALENDAR, vm.state.value.view)
    }

    @Test
    fun `TC-9736 화면에 다시 돌아오면 지금 달과 목록을 다시 읽는다(삭제 뒤 갱신)`() {
        val vm = viewModel()
        vm.onViewSelected(RecordView.LIST)
        vm.onResumed()
        assertEquals(2, records.runsCalls.size, "처음 표시는 init이 읽는다")
        vm.onResumed()
        assertEquals(4, records.runsCalls.size)
    }

    @Test
    fun `달을 옮기면 그 달을 읽고 고른 날은 그 달 마지막 기록 날로 바뀐다`() {
        val vm = viewModel()
        vm.onPreviousMonth()
        assertEquals(YearMonth.of(2026, 9), vm.state.value.month)
        assertEquals(LocalDate.of(2026, 9, 1), records.runsCalls.last().second)
        vm.onNextMonth()
        vm.onNextMonth()
        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
    }

    @Test
    fun `미전송 기록 수를 함께 보이고 목록 오류는 다시 읽는다(FR-039)`() {
        pending.runs.value = listOf(pendingRun("p1"))
        records.runsResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        assertEquals(1, vm.state.value.pendingCount)
        assertNotNull(vm.state.value.error)
        records.runsResult = Outcome.Success(RecordList(emptyList()))
        vm.refresh()
        assertNull(vm.state.value.error)
    }
}
