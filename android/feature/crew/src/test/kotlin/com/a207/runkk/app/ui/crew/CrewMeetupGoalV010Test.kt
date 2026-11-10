package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.Meetup
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 주간 공동 목표(와이어프레임 M8): 화면 동작과 라이트·다크 스크린샷. */
@RunWith(RobolectricTestRunner::class)
class CrewMeetupGoalScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private val goalState = CrewGoalSetUiState(
        crewId = 11,
        weekStart = LocalDate.of(2026, 10, 19),
        draft = "80",
        pastWeeks = listOf(
            raceWithGoal.copy(
                goal = com.a207.runkk.app.core.model.Distance(80_000),
                goalProgressPercent = 73,
                achievedAt = null
            ),
            raceWithGoal.copy(
                closesAt = raceWithGoal.closesAt.minus(java.time.Duration.ofDays(7)),
                goal = com.a207.runkk.app.core.model.Distance(50_000),
                goalProgressPercent = 117,
                achievedAt = java.time.Instant.parse("2026-10-10T10:00:00Z")
            )
        )
    )

    @Test
    fun `주간 목표 - 주차 제목, 지난 목표 카드(달성 실패·달성, 100 넘는 진행률 그대로), 저장(피그마 F-07, FR-138)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewGoalSetScreen(goalState, GoalSetActions(onSave = { calls += "save" }))
            }
        }
        compose.onNodeWithText("10월 4주차").assertExists()
        compose.onNodeWithText("10.19 (월) ~ 10.25 (일)").assertExists()
        compose.onNodeWithText("지난 목표").assertExists()
        compose.onNode(hasContentDescription("달성 실패", substring = true)).assertExists()
        compose.onNode(hasContentDescription("10월 3주차, 58.6킬로미터 달림, 달성")).assertExists()
        compose.onNodeWithText("117%", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("빠른 선택").assertDoesNotExist()
        compose.onNodeWithText("저장").performClick()
        assertEquals(listOf("save"), calls)
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun goalDark() = compose.captureRunkkScreen("M8_crew_goal_set_dark", isDarkTheme = true) {
        CrewGoalSetScreen(goalState, GoalSetActions())
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun goalLight() = compose.captureRunkkScreen("M8_crew_goal_set_light", isDarkTheme = false) {
        CrewGoalSetScreen(goalState, GoalSetActions())
    }
}

/** 대면 모임 상세(M7)와 주간 공동 목표(M8) ViewModel. */
class CrewMeetupGoalViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    @Test
    fun `주간 목표 - 지난 3주를 race 세 번으로 읽고 1km 미만은 막고 정하면 닫는다(FR-138, D-성-32)`() {
        val vm = CrewGoalSetViewModel(repo, MutableClock()).also { it.bind(11) }
        assertEquals(LocalDate.of(2026, 10, 19), vm.state.value.weekStart)
        assertEquals(3, vm.state.value.pastWeeks.size)
        assertEquals(4, repo.calls.count { it == "race" }, "이번 주 1번 + 지난 3주")
        vm.onDraft("0.5")
        vm.onSave()
        assertTrue(repo.calls.none { it.startsWith("goal:") })
        vm.onDraft("80")
        vm.onSave()
        assertTrue("goal:80000" in repo.calls)
        assertTrue(vm.state.value.isSaved)
    }

    @Test
    fun `주간 목표 - 크루를 만들기 전 주는 지난 목표에 넣지 않는다(DV-성 9(c))`() {
        // 이번 주 월요일 2026-10-19, 크루 개설 10-08(목): 10-12 주와 10-05 주(개설일이 든 주)만, 09-28 주는 뺀다.
        repo.createdAt = java.time.Instant.parse("2026-10-08T03:00:00Z")
        val vm = CrewGoalSetViewModel(repo, MutableClock()).also { it.bind(11) }
        assertEquals(2, vm.state.value.pastWeeks.size)
        assertEquals(3, repo.calls.count { it == "race" }, "이번 주 1번 + 개설 뒤 지난 2주")
    }
}
