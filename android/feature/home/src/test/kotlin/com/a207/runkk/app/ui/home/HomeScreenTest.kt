package com.a207.runkk.app.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.runkkAvatarCharacterTag
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.record.WeeklyGoalProgress
import com.a207.runkk.app.domain.run.FreeRunGoal
import com.a207.runkk.app.domain.run.PlanSnapshot
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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
                isLoading = false,
                dashboard = sampleDashboard,
                today = LocalDate.of(2026, 10, 20),
                freeRunGoal = FreeRunGoal(PlanSnapshot(Distance(5_000), null), isFromCoachPlan = true)
            )
        )
        compose.onNodeWithText("자유런 시작").performScrollTo().performClick()
        compose.onNodeWithContentDescription("자유런 설정").performScrollTo().performClick()
        assertEquals(listOf(Routes.RUN, Routes.FREE_RUN_SETTINGS), routes)
        compose.onNodeWithText("5K").assertExists()
    }

    @Test
    fun `알림함·로고에 접근성 라벨이 있다`() {
        show(HomeUiState(isLoading = false))
        compose.onNodeWithContentDescription("RRun 런크크").assertExists()
        compose.onNodeWithContentDescription("알림함").performClick()
        assertEquals(listOf(Routes.NOTIFICATIONS), routes)
    }

    @Test
    fun `배틀런 탭에서는 랜덤 대결로 간다`() {
        show(HomeUiState(isLoading = false, mode = HomeRunMode.BATTLE))
        compose.onNodeWithText("배틀런 시작").performScrollTo().performClick()
        assertEquals(listOf(Routes.BATTLE_QUICK_MATCH), routes, "홈 배틀런은 기본 대결 거리로 바로 매칭한다(FR-080)")
    }

    @Test
    fun `미전송 기록이 있으면 안내를 누르면 기록 목록으로 간다`() {
        show(HomeUiState(isLoading = false, pendingCount = 2))
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
        show(HomeUiState(isLoading = false, dashboard = dashboard, isHeaderLoading = false))
        compose.onNodeWithContentDescription("오늘 3.02킬로미터, 18분 20초, 2회").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("이번 주 목표 거리 60% 달성, 4회 중 2회")
            .performScrollTo()
            .assertHasClickAction()
    }

    @Test
    fun `오늘 기록이 없으면 안내 문구, 목표가 없으면 목표 안내를 읽는다`() {
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals(res.getString(R.string.home_today_not_run), todayCaption(res, sampleDashboard))
        assertEquals("아직 달리지 않았어요", todayCaption(res, sampleDashboard))
        assertEquals(
            res.getString(R.string.home_summary_set_goal),
            weeklySpoken(res, sampleDashboard.copy(weeklyAchievement = null))
        )
        assertEquals("목표를 정해 보세요", weeklySpoken(res, sampleDashboard.copy(weeklyAchievement = null)))
        assertEquals(
            res.getString(R.string.home_week_spoken_distance, 135),
            weeklySpoken(res, sampleDashboard.copy(weeklyAchievement = 1.35))
        )
        assertEquals("거리 135% 달성", weeklySpoken(res, sampleDashboard.copy(weeklyAchievement = 1.35)))
    }

    @Test
    fun `오늘 계획 칩은 거리·페이스를 말로 읽는다`() {
        show(
            HomeUiState(
                isLoading = false,
                isHeaderLoading = false,
                freeRunGoal =
                    FreeRunGoal(
                        PlanSnapshot(Distance(5_000), com.a207.runkk.app.core.model.Pace(330)),
                        isFromCoachPlan = true
                    )
            )
        )
        compose.onNodeWithText("오늘 계획 그대로").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("오늘 목표 5킬로미터").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("목표 페이스 1킬로미터당 5분 30초").performScrollTo().assertExists()
    }

    @Test
    fun `설정 목표면 지난번 설정 그대로와 목표 거리 칩을 보인다(FR-048)`() {
        show(
            HomeUiState(
                isLoading = false,
                isHeaderLoading = false,
                freeRunGoal = FreeRunGoal(PlanSnapshot(Distance(3_000), null), isFromCoachPlan = false)
            )
        )
        compose.onNodeWithText("지난번 설정 그대로").performScrollTo().assertExists()
        compose.onNodeWithText("3K").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("목표 3킬로미터").performScrollTo().assertExists()
    }

    @Test
    fun `계획이 없으면 목표 없이 달리기 칩`() {
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals(listOf(res.getString(R.string.home_plan_none)), planChips(res, null))
        assertEquals(listOf("목표 없이 달리기"), planChips(res, null))
        assertEquals(listOf("5K"), planChips(res, PlanSnapshot(Distance(5_000), null)))
    }

    @Test
    fun `상단은 닉네임·티어·레벨과 리워드 잔액(상점으로)을 보인다`() {
        show(HomeUiState(isLoading = false, isHeaderLoading = false, header = HomeHeader("달리는곰", "골드", 3, 1_250)))
        compose.onNodeWithText("달리는곰").assertExists()
        compose.onNodeWithText("골드 · Lv.3").assertExists()
        compose.onNodeWithContentDescription("리워드 잔액 1,250").performClick()
        assertEquals(listOf(Routes.SHOP), routes)
    }

    @Test
    fun `기본 캐릭터면 상단 프로필 원과 우리 집 아바타 자리에 캐릭터 그림을 그린다`() {
        show(HomeUiState(isLoading = false, isHeaderLoading = false, faceKey = "default:BEAR"))
        compose.onAllNodesWithTag(runkkAvatarCharacterTag("BEAR"), useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `얼굴이 없거나 모르는 값이면 캐릭터 그림 없이 자리 그림이다`() {
        show(HomeUiState(isLoading = false, isHeaderLoading = false, faceKey = "default:DRAGON"))
        compose.onNodeWithText("아바타").assertExists()
        RunkkAvatarArt.characters.keys.forEach {
            compose.onNodeWithTag(runkkAvatarCharacterTag(it), useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `프로필을 못 읽었으면 자리를 기본 문구로 채운다`() {
        show(HomeUiState(isLoading = false, isHeaderLoading = false))
        compose.onNodeWithText("언랭 · Lv.-").assertExists()
        compose.onNodeWithContentDescription("리워드 잔액 -").assertExists()
    }
}
