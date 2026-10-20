package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val pending = FakePendingRunRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Seoul"))

    private fun viewModel() = HomeViewModel(records, pending, clock)

    @Test
    fun `홈은 오늘 날짜의 대시보드와 오늘 계획을 읽는다`() {
        val vm = viewModel()
        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(sampleDashboard, state.dashboard)
        assertEquals(Distance(5_000), state.todayPlan?.distance)
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), records.dashboardDates)
    }

    @Test
    fun `미전송 기록 수는 보관함 변화를 따라간다(FR-039)`() {
        val vm = viewModel()
        assertEquals(0, vm.state.value.pendingCount)
        pending.pendingRuns.value = listOf(completedRun("a"), completedRun("b"))
        assertEquals(2, vm.state.value.pendingCount)
    }

    @Test
    fun `대시보드를 못 읽으면 오류를 보이고 다시 읽는다`() {
        records.dashboardResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        assertTrue(vm.state.value.error!!.startsWith("네트워크"))
        assertNull(vm.state.value.dashboard)
        records.dashboardResult = Outcome.Success(sampleDashboard)
        vm.refresh()
        assertNull(vm.state.value.error)
        assertEquals(sampleDashboard, vm.state.value.dashboard)
    }

    @Test
    fun `오늘 계획이 없어도 대시보드는 보인다`() {
        records.todayPlanResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        assertNull(vm.state.value.todayPlan)
        assertEquals(sampleDashboard, vm.state.value.dashboard)
    }

    @Test
    fun `러닝 종류 탭을 바꾼다`() {
        val vm = viewModel()
        assertEquals(HomeRunMode.FREE, vm.state.value.mode)
        vm.onModeSelected(HomeRunMode.BATTLE)
        assertEquals(HomeRunMode.BATTLE, vm.state.value.mode)
    }
}
