package com.a207.runkk.app.ui.home

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.record.WeeklyGoalProgress
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val routes = mutableListOf<String>()

    private fun show(state: HomeUiState, onMode: (HomeRunMode) -> Unit = {}) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                HomeScreen(state, onModeSelected = onMode, onNavigate = {
                    routes +=
                        it
                }, onRetry = {})
            }
        }
    }

    @Test
    fun `자유런 시작은 러닝 화면으로, 설정은 자유런 설정으로 간다`() {
        show(
            HomeUiState(
                loading = false,
                dashboard = sampleDashboard,
                today = LocalDate.of(2026, 10, 20),
                todayPlan = TodayPlan(Distance(5_000), null)
            )
        )
        compose.onNodeWithText("자유런 시작").performScrollTo().performClick()
        compose.onNodeWithContentDescription("자유런 설정").performScrollTo().performClick()
        assertEquals(listOf(Routes.RUN, Routes.FREE_RUN_SETTINGS), routes)
        compose.onNodeWithText("5K").assertExists()
    }

    @Test
    fun `알림함·로고에 접근성 라벨이 있다`() {
        show(HomeUiState(loading = false))
        compose.onNodeWithContentDescription("RRun 런크크").assertExists()
        compose.onNodeWithContentDescription("알림함").performClick()
        assertEquals(listOf(Routes.NOTIFICATIONS), routes)
    }

    @Test
    fun `배틀런 탭에서는 랜덤 대결로 간다`() {
        show(HomeUiState(loading = false, mode = HomeRunMode.BATTLE))
        compose.onNodeWithText("배틀런 시작").performScrollTo().performClick()
        assertEquals(listOf(Routes.BATTLE_RANDOM), routes)
    }

    @Test
    fun `미전송 기록이 있으면 안내를 누르면 기록 목록으로 간다`() {
        show(HomeUiState(loading = false, pendingCount = 2))
        compose.onNodeWithText("미전송 기록 2건 · 연결되면 자동으로 보내요").performScrollTo().performClick()
        assertEquals(listOf(Routes.RECORDS), routes)
    }

    @Test
    fun `오늘 칸은 시간·횟수를, 이번 주 칸은 거리 달성률(비율 × 100)을 보인다`() {
        val dashboard = sampleDashboard.copy(
            todayElapsed = Duration.ofSeconds(1_100),
            todayRuns = 2,
            weekly = WeeklyGoalProgress(4, Distance(20_000), 2, Distance(12_000), 50, 60)
        )
        show(HomeUiState(loading = false, dashboard = dashboard, headerLoading = false))
        compose.onNodeWithContentDescription("오늘 3.02킬로미터, 18분 20초, 2회").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("이번 주 목표 거리 60% 달성, 4회 중 2회")
            .performScrollTo()
            .assertHasClickAction()
    }

    @Test
    fun `오늘 기록이 없으면 안내 문구, 목표가 없으면 목표 안내를 읽는다`() {
        assertEquals("아직 달리지 않았어요", todayCaption(sampleDashboard))
        assertEquals("목표를 정해 보세요", weeklySpoken(sampleDashboard.copy(weeklyAchievement = null)))
        assertEquals("거리 135% 달성", weeklySpoken(sampleDashboard.copy(weeklyAchievement = 1.35)))
    }

    @Test
    fun `오늘 계획 칩은 거리·페이스를 말로 읽는다`() {
        show(
            HomeUiState(
                loading = false,
                headerLoading = false,
                todayPlan = TodayPlan(Distance(5_000), com.a207.runkk.app.core.model.Pace(330))
            )
        )
        compose.onNodeWithContentDescription("오늘 목표 5킬로미터").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("목표 페이스 1킬로미터당 5분 30초").performScrollTo().assertExists()
    }

    @Test
    fun `계획이 없으면 목표 없이 달리기 칩`() {
        assertEquals(listOf("목표 없이 달리기"), planChips(null))
        assertEquals(listOf("5K"), planChips(TodayPlan(Distance(5_000), null)))
    }

    @Test
    fun `상단은 닉네임·티어·레벨과 리워드 잔액(상점으로)을 보인다`() {
        show(HomeUiState(loading = false, headerLoading = false, header = HomeHeader("달리는곰", "골드", 3, 1_250)))
        compose.onNodeWithText("달리는곰").assertExists()
        compose.onNodeWithText("골드 · Lv.3").assertExists()
        compose.onNodeWithContentDescription("리워드 잔액 1,250").performClick()
        assertEquals(listOf(Routes.SHOP), routes)
    }

    @Test
    fun `프로필을 못 읽었으면 자리를 기본 문구로 채운다`() {
        show(HomeUiState(loading = false, headerLoading = false))
        compose.onNodeWithText("언랭 · Lv.-").assertExists()
        compose.onNodeWithContentDescription("리워드 잔액 -").assertExists()
    }
}
