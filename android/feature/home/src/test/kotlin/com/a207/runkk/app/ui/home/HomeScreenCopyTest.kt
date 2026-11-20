package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.run.TodayPlanCache
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * 홈 첫 화면 사본(S15P31A207-345 2차): 시작 로딩이 남긴 오늘 대시보드·내 요약 사본이 있으면 먼저 그리고, 서버에서 새로 읽은 값이 오면
 * 바꾼다. 새로 읽지 못해도 사본(지난 값)을 남긴다.
 */
class HomeScreenCopyTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val account = FakeAccountRepository()
    private val settings = FakeSettingsRepository()
    private val clock = HomeRefreshGateTest.MutableClock(Instant.parse("2026-10-20T01:00:00Z"))
    private val today = LocalDate.of(2026, 10, 20)

    private fun viewModel() = HomeViewModel(
        records,
        account,
        LocationConsentUseCase(account, settings),
        TodayPlanCache(),
        FakePendingRunRepository(),
        clock,
        settings
    )

    @Test
    fun `새로 읽지 못해도 받아 둔 내 요약과 대시보드 사본을 그린다`() {
        records.dashboardCopy = today to sampleDashboard
        account.summaryCopy = MySummary("사본곰", "실버", 2, null, null, 3)
        records.dashboardResult = Outcome.Failure(AppError.Network)
        account.summaryResult = Outcome.Failure(AppError.Network)

        val state = viewModel().state.value
        assertEquals("사본곰", state.header.nickname)
        assertFalse(state.isHeaderLoading)
        assertFalse(state.isLoading)
    }

    @Test
    fun `새로 읽은 내 요약이 사본을 바꾼다`() {
        records.dashboardCopy = today to sampleDashboard
        account.summaryCopy = MySummary("사본곰", "실버", 2, null, null, 3)

        val state = viewModel().state.value
        assertEquals("달리는곰", state.header.nickname)
    }
}
