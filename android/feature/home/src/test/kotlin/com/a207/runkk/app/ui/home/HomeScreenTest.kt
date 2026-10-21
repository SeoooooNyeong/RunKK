package com.a207.runkk.app.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.record.TodayPlan
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
