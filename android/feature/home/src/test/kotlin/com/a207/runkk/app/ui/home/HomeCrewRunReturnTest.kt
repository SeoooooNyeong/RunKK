@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.HomeRunReturnReceipt
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.run.TodayPlanCache
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * 크루런 결과 「확인」 뒤 홈 이번 주 목표(설계 05-32 흐름 4, TC-11016·11035·11037 홈 부분): 복귀 값을 받으면 대시보드를 다시 읽고,
 * 읽기에 성공한 첫 표시부터 내 유효 거리를 2초 강조한다. 같은 복귀 값은 한 번만 강조하고 거리를 손으로 더하지 않는다.
 */
class HomeCrewRunReturnTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val account = FakeAccountRepository()

    private fun viewModel() = HomeViewModel(
        records,
        account,
        LocationConsentUseCase(account, FakeSettingsRepository()),
        TodayPlanCache(),
        FakePendingRunRepository(),
        clock
    )

    private fun receipt(id: String = "rec-1", meters: Long = 800) =
        HomeRunReturnReceipt(id, 31, id, meters, Instant.parse("2026-10-20T01:00:00Z"))

    @Test
    fun `TC-11016 복귀 값을 받으면 대시보드를 다시 읽고 서버 거리를 그대로 보이며 내 거리를 강조한다`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onResumed()
        val reads = records.dashboardDates.size
        val before = vm.state.value.weeklyDistance
        vm.onResumed(receipt())
        runCurrent()
        assertEquals(reads + 1, records.dashboardDates.size, "첫 재개여도 복귀 값이면 다시 읽는다")
        assertEquals(Distance(800), vm.state.value.weeklyHighlight)
        assertEquals(before, vm.state.value.weeklyDistance, "거리는 서버 원본 그대로이고 손으로 더하지 않는다")
    }

    @Test
    fun `TC-11037 홈 강조는 1,999ms까지 보이고 2,000ms에 끝난다`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onResumed(receipt())
        runCurrent()
        advanceTimeBy(1_999)
        assertEquals(Distance(800), vm.state.value.weeklyHighlight)
        advanceTimeBy(1)
        runCurrent()
        assertNull(vm.state.value.weeklyHighlight)
    }

    @Test
    fun `TC-11035 같은 복귀 값은 한 번만 강조하고 유효 거리 0이면 강조하지 않는다`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onResumed(receipt())
        runCurrent()
        advanceTimeBy(2_001)
        val reads = records.dashboardDates.size
        vm.onResumed(receipt())
        runCurrent()
        assertNull(vm.state.value.weeklyHighlight, "같은 기록은 다시 강조하지 않는다")
        assertEquals(reads + 1, records.dashboardDates.size, "재개라서 읽기는 한다")
        vm.onResumed(receipt("rec-2", meters = 0))
        runCurrent()
        assertNull(vm.state.value.weeklyHighlight, "valid=false(유효 거리 0)는 강조하지 않는다")
    }

    @Test
    fun `대시보드를 못 읽으면 강조를 미루고 다음 읽기에 한 번 강조한다`() = runTest(main.dispatcher) {
        records.dashboardResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        vm.onResumed(receipt())
        runCurrent()
        assertNull(vm.state.value.weeklyHighlight)
        records.dashboardResult = Outcome.Success(sampleDashboard)
        vm.onResumed()
        runCurrent()
        assertEquals(Distance(800), vm.state.value.weeklyHighlight)
        advanceTimeBy(2_001)
    }
}
