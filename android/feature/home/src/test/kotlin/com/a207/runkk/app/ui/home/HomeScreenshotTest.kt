package com.a207.runkk.app.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.TodayPlan
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 홈(A5) 스크린샷(가짜 UiState, 390×844dp 다크). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 21)
    private val dashboard = Dashboard(
        today = Distance(3_020),
        total = Distance(128_400),
        landmark = "한강 다리 12개",
        streakDays = 4,
        grass = (0L..27L).filter { it % 3 != 0L }.associate { today.minusDays(it) to Distance(4_000) },
        weeklyAchievement = 0.6,
        crew = Dashboard.CrewArea("새벽크루", 9, emptyMap(), "남산 3바퀴"),
        todayElapsed = java.time.Duration.ofSeconds(1_004),
        todayRuns = 1,
        grassLevels = (0L..27L).filter { it % 3 != 0L }.associate { today.minusDays(it) to (it % 4 + 1).toInt() }
    )

    @Test
    fun home() = compose.captureRunkkScreen("A5_home") {
        HomeScreen(
            HomeUiState(
                loading = false,
                today = today,
                dashboard = dashboard,
                todayPlan = TodayPlan(Distance(5_000), Pace(330)),
                pendingCount = 1,
                header = HomeHeader("달리는곰", "골드", 3, 1_250),
                headerLoading = false
            ),
            onModeSelected = {},
            onNavigate = {},
            onRetry = {}
        )
    }

    @Test
    fun homeLoading() = compose.captureRunkkScreen("A5_home_loading") {
        HomeScreen(HomeUiState(), onModeSelected = {}, onNavigate = {}, onRetry = {})
    }

    @Test
    fun homeBattleTab() = compose.captureRunkkScreen("A5_home_battle_tab") {
        HomeScreen(
            HomeUiState(
                loading = false,
                today = today,
                dashboard = dashboard,
                mode = HomeRunMode.BATTLE,
                header = HomeHeader("달리는곰", null, 1, 0),
                headerLoading = false
            ),
            onModeSelected = {},
            onNavigate = {},
            onRetry = {}
        )
    }

    @Test
    fun homeOffline() = compose.captureRunkkScreen("A5_home_error") {
        HomeScreen(
            HomeUiState(loading = false, headerLoading = false, error = "네트워크에 연결할 수 없어요. 연결을 확인하고 다시 시도해 주세요"),
            onModeSelected = {},
            onNavigate = {},
            onRetry = {}
        )
    }
}
