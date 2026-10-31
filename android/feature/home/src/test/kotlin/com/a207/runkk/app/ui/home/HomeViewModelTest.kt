package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.TodayPlanCache
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** 홈(A-4, D-276): 상단·주간 막대·연속·리워드 잔액과 자유런 시작 전 위치 동의(D-267). 시험 설계 10-06 TC-9721·TC-9684·TC-9685. */
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val account = FakeAccountRepository()
    private val settings = FakeSettingsRepository()
    private val todayPlans = TodayPlanCache()

    private fun viewModel() =
        HomeViewModel(records, account, LocationConsentUseCase(account, settings), todayPlans, clock)

    @Test
    fun `오늘 계획을 읽으면 앱의 오늘 계획 사본에 알린다(워치 시작 화면, FR-042)`() {
        viewModel()
        assertEquals(PlanSnapshot(Distance(5_000), Pace(330)), todayPlans.plan.value)
    }

    @Test
    fun `오늘 계획을 읽지 못하면 앱의 오늘 계획 사본은 그대로 둔다`() {
        val known = PlanSnapshot(Distance(7_000), null)
        todayPlans.put(known)
        records.todayPlanResult = Outcome.Failure(AppError.Network)
        viewModel()
        assertEquals(known, todayPlans.plan.value)
    }

    @Test
    fun `TC-9721 홈은 오늘 날짜의 대시보드에서 이번 주 거리와 목표, 연속 일수를 읽는다`() {
        val state = viewModel().state.value
        assertFalse(state.isLoading)
        assertEquals(Distance(12_000), state.weeklyDistance)
        assertEquals(Distance(20_000), state.weeklyGoal)
        assertEquals(4, state.streakWeeks)
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), records.dashboardDates)
    }

    @Test
    fun `TC-9721 상단은 본인 닉네임·티어·레벨과 대시보드의 리워드 잔액이다(D-276)`() {
        val header = viewModel().state.value.header
        assertEquals(HomeHeader(nickname = "달리는곰", tier = "골드", level = 3, balance = 1_250), header)
        assertEquals(1, account.summaryCalls)
        assertEquals(emptyList(), account.profileIds, "프로필 전체를 읽지 않는다(R53)")
    }

    @Test
    fun `TC-9743 옛 서버라 잔액·주간 목표가 없으면 그 자리를 비운다`() {
        records.dashboardResult = Outcome.Success(sampleDashboard.copy(weekly = null, rewardBalance = null))
        val state = viewModel().state.value
        assertNull(state.header.balance)
        assertNull(state.weeklyGoal)
        assertEquals(Distance.ZERO, state.weeklyDistance)
    }

    @Test
    fun `자유런 카드의 음성·진동은 러닝 설정 값이다`() {
        account.settingsResult = Outcome.Success(
            MemberSettings(
                values = mapOf("RUN_SETTINGS.voice" to "false", "RUN_SETTINGS.vibration" to "true"),
                consents = emptyMap()
            )
        )
        val state = viewModel().state.value
        assertFalse(state.isRunVoiceOn)
        assertTrue(state.isRunVibrationOn)
    }

    @Test
    fun `아바타 얼굴·배경 키는 본인 프로필 값이다(아바타 꾸미기가 열렸을 때만 그린다)`() {
        account.summaryResult = Outcome.Success(MySummary("달리는곰", "골드", 3, "default:BEAR", "BUSAN", 12))
        val state = viewModel().state.value
        assertEquals("default:BEAR", state.faceKey)
        assertEquals("BUSAN", state.backgroundKey)
    }

    @Test
    fun `대시보드를 못 읽으면 오류를 보이고 다시 읽는다`() {
        records.dashboardResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.error)
        records.dashboardResult = Outcome.Success(sampleDashboard)
        vm.refresh()
        assertNull(vm.state.value.error)
        assertEquals(4, vm.state.value.streakWeeks)
    }

    @Test
    fun `AN8-11 다시 읽을 때 대시보드만 실패하면 리워드 잔액은 지난 값을 남긴다`() {
        val vm = viewModel()
        records.dashboardResult = Outcome.Failure(AppError.Network)
        vm.refresh()
        assertEquals(1_250, vm.state.value.header.balance)
        assertEquals("달리는곰", vm.state.value.header.nickname)
    }

    @Test
    fun `프로필을 못 읽어도 홈은 보이고 그 자리는 비운다`() {
        account.summaryResult = Outcome.Failure(AppError.Network)
        val state = viewModel().state.value
        assertEquals(HomeHeader(balance = 1_250), state.header)
        assertFalse(state.isHeaderLoading)
    }

    @Test
    fun `처음 재개는 다시 읽지 않고, 다시 보일 때마다 다시 읽는다`() {
        val vm = viewModel()
        vm.onResumed()
        assertEquals(1, records.dashboardDates.size)
        records.dashboardResult = Outcome.Success(sampleDashboard.copy(streakWeeks = 5))
        vm.onResumed()
        assertEquals(2, records.dashboardDates.size)
        assertEquals(5, vm.state.value.streakWeeks)
    }

    @Test
    fun `TC-9685 위치 동의가 있으면 자유런 카드는 바로 시작한다`() = runTest {
        settings.isLocationConsented = true
        val vm = viewModel()
        vm.onFreeRunTapped()
        assertEquals(HomeEvent.StartRun, vm.events.first())
        assertFalse(vm.state.value.isConsentSheetVisible)
    }

    @Test
    fun `TC-9685 위치 동의가 없으면 시작 전에 시트를 띄우고 닫기는 시작하지 않는다`() {
        val vm = viewModel()
        vm.onFreeRunTapped()
        assertTrue(vm.state.value.isConsentSheetVisible)
        vm.onConsentClosed()
        assertFalse(vm.state.value.isConsentSheetVisible)
        assertEquals(emptyList(), account.consents)
    }

    @Test
    fun `TC-9685 동의하고 시작이면 위치 동의를 주고 사본을 갱신한 뒤 시작한다`() = runTest {
        val vm = viewModel()
        vm.onFreeRunTapped()
        vm.onConsentAgreed()
        assertEquals(listOf("LOCATION"), account.consents)
        assertEquals(1, settings.refreshCalls)
        assertFalse(vm.state.value.isConsentSheetVisible)
        assertEquals(HomeEvent.StartRun, vm.events.first())
    }

    @Test
    fun `TC-9684 동의가 실패하면 시작하지 않고 실패를 알린다`() {
        account.consentResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        vm.onFreeRunTapped()
        vm.onConsentAgreed()
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.message)
        assertEquals(0, settings.refreshCalls)
        vm.onMessageShown()
        assertNull(vm.state.value.message)
    }
}
