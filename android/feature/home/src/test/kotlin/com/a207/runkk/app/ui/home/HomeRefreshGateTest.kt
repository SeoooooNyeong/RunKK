package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.HomeRunReturnReceipt
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.account.PlacementProgress
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.run.TodayPlanCache
import com.a207.runkk.app.domain.settings.GuidanceSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 홈 복귀 스레드(HomeViewModel → RefreshGate → 저장소 가짜, Jira 325): 30초 안 재진입은 서버를 부르지 않고, 다시 시도·새 복귀 영수증·
 * 날짜 바뀜은 간격을 무시한다. 설정·배치 진행은 기기 사본을 읽고 사본이 없을 때만 서버를 부른다.
 */
class HomeRefreshGateTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val account = FakeAccountRepository()
    private val settings = FakeSettingsRepository()
    private val clock = MutableClock(Instant.parse("2026-10-20T01:00:00Z"))

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
    fun `30초 안에 다시 보이면 서버를 부르지 않고 30초가 지나면 다시 읽는다`() {
        val vm = viewModel()
        vm.onResumed()
        clock.now = clock.now.plusSeconds(29)
        vm.onResumed()
        assertEquals(1, records.dashboardDates.size, "30초 안 재진입은 요청 0건")
        assertEquals(1, account.summaryCalls)

        clock.now = clock.now.plusSeconds(1)
        vm.onResumed()
        assertEquals(2, records.dashboardDates.size)
        assertEquals(2, account.summaryCalls)
    }

    @Test
    fun `다시 시도(당겨서 새로고침)는 간격을 무시한다`() {
        val vm = viewModel()
        vm.refresh()
        assertEquals(2, records.dashboardDates.size)
    }

    @Test
    fun `새 복귀 영수증은 간격을 무시하고, 이미 쓴 영수증은 간격을 따른다`() {
        val vm = viewModel()
        vm.onResumed(receipt("rec-1"))
        assertEquals(2, records.dashboardDates.size, "기록 저장 뒤 복귀는 바로 다시 읽는다")
        vm.onResumed(receipt("rec-1"))
        assertEquals(2, records.dashboardDates.size, "같은 영수증은 강제가 아니다")
    }

    @Test
    fun `오늘 날짜가 바뀌면 30초 안이어도 다시 읽는다`() {
        clock.now = Instant.parse("2026-10-19T14:59:50Z") // KST 23:59:50
        val vm = viewModel()
        vm.onResumed() // 첫 재개(init이 이미 읽음)
        clock.now = Instant.parse("2026-10-19T15:00:05Z") // KST 다음 날 00:00:05
        vm.onResumed()
        assertEquals(2, records.dashboardDates.size)
        assertEquals(java.time.LocalDate.of(2026, 10, 20), records.dashboardDates.last())
    }

    @Test
    fun `읽기에 실패하면 시각을 남기지 않아 바로 다시 읽는다`() {
        records.dashboardResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        vm.onResumed() // 첫 재개(init이 이미 읽음)
        clock.now = clock.now.plusSeconds(1)
        vm.onResumed()
        assertEquals(2, records.dashboardDates.size)
    }

    @Test
    fun `설정 사본이 있으면 서버 설정을 부르지 않고 사본 값을 쓴다`() {
        settings.hasCopy = true
        settings.guidance = GuidanceSettings.DEFAULT.copy(isRunVoiceOn = false, isRunVibrationOn = true)
        val vm = viewModel()
        assertEquals(0, account.settingsCalls)
        assertFalse(vm.state.value.isRunVoiceOn)
        assertTrue(vm.state.value.isRunVibrationOn)

        // 설정 화면에서 바꾸고 30초 안에 돌아와도 사본 값을 바로 보인다(서버 요청 없음).
        settings.guidance = GuidanceSettings.DEFAULT.copy(isRunVoiceOn = true, isRunVibrationOn = false)
        vm.onResumed()
        vm.onResumed()
        assertTrue(vm.state.value.isRunVoiceOn)
        assertFalse(vm.state.value.isRunVibrationOn)
        assertEquals(0, account.settingsCalls)
    }

    @Test
    fun `설정 사본이 없을 때만 서버에서 읽는다`() {
        settings.hasCopy = false
        viewModel()
        assertEquals(1, account.settingsCalls)
    }

    @Test
    fun `배치 진행 사본이 있으면 서버를 부르지 않고, 다시 시도는 서버에서 다시 읽는다`() {
        account.placementCopy = PlacementProgress(PlacementStatus.IN_PROGRESS, 1, 2, "BRONZE", "UNRANKED", emptyList())
        val vm = viewModel()
        assertEquals(0, account.placementCalls)
        assertEquals(account.placementCopy, vm.state.value.placement)

        vm.refresh()
        assertEquals(1, account.placementCalls)
    }

    private fun receipt(id: String) = HomeRunReturnReceipt(id, 31, id, 800, Instant.parse("2026-10-20T01:00:00Z"))

    /** 바꿀 수 있는 시계(UTC). withZone은 같은 시각을 그 시간대로 읽는다. */
    class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock {
            val base = this
            return object : Clock() {
                override fun getZone(): ZoneId = zone

                override fun withZone(other: ZoneId): Clock = base.withZone(other)

                override fun instant(): Instant = base.now
            }
        }

        override fun instant(): Instant = now
    }
}
