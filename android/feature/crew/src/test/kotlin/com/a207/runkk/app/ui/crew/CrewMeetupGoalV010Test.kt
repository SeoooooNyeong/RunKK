package com.a207.runkk.app.ui.crew

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

/** 대면 모임 상세(와이어프레임 M7)와 주간 공동 목표(M8): 화면 동작과 라이트·다크 스크린샷. */
@RunWith(RobolectricTestRunner::class)
class CrewMeetupGoalScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private val meetupState = CrewMeetupDetailUiState(
        crewId = 11,
        postId = 71,
        post = meetups.posts[0].copy(
            meetup = Meetup(start.plusSeconds(172_800), "여의나루역 2번 출구", 8, 6, 1, 2, AttendanceAnswer.GOING)
        ),
        now = start,
        isLoading = false
    )

    private val goalState = CrewGoalSetUiState(
        crewId = 11,
        weekStart = LocalDate.of(2026, 10, 19),
        draft = "80",
        lastWeek = raceWithGoal.copy(goal = com.a207.runkk.app.core.model.Distance(80_000), achievedAt = null)
    )

    @Test
    fun `대면 모임 - 참석과 정원, 응답 수를 보이고 응답을 바꾼다(FR-145)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewMeetupDetailScreen(
                    meetupState,
                    MeetupDetailActions(onAnswer = { calls += "answer:$it" }, onReport = { calls += "report" })
                )
            }
        }
        compose.onNodeWithText("참석 6 / 정원 8명").assertExists()
        compose.onNodeWithText("참석 6, 불참 1, 미정 2").assertExists()
        compose.onNodeWithText("미정").performClick()
        compose.onNodeWithContentDescription("일정 메뉴").performClick()
        compose.onNodeWithText("신고").performClick()
        assertEquals(listOf("answer:UNDECIDED", "report"), calls)
    }

    @Test
    fun `공동 목표 - 빠른 선택과 지난 주 결과, 저장(FR-138)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewGoalSetScreen(
                    goalState,
                    GoalSetActions(onQuick = { calls += "quick:$it" }, onSave = {
                        calls +=
                            "save"
                    })
                )
            }
        }
        compose.onNodeWithText("이번 주 10.19 (월) ~ 10.25 (일)").assertExists()
        compose.onNodeWithText("달성 못 함").assertExists()
        compose.onNodeWithText("58.6 / 80.0 km").assertExists()
        compose.onNodeWithText("참여 3명").assertExists()
        compose.onNodeWithText("100km").performClick()
        compose.onNodeWithText("저장").performClick()
        assertEquals(listOf("quick:100", "save"), calls)
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun meetupDark() = compose.captureRunkkScreen("M7_crew_meetup_detail_dark", isDarkTheme = true) {
        CrewMeetupDetailScreen(meetupState, MeetupDetailActions())
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun meetupLight() = compose.captureRunkkScreen("M7_crew_meetup_detail_light", isDarkTheme = false) {
        CrewMeetupDetailScreen(meetupState, MeetupDetailActions())
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
    fun `대면 모임 - 목록에서 글을 찾고 응답 뒤 다시 읽는다`() {
        val vm = CrewMeetupDetailViewModel(repo, MutableClock()).also { it.bind(11, 71) }
        assertEquals("여의나루역 2번 출구", vm.state.value.post?.meetup?.placeName)
        vm.onAnswer(AttendanceAnswer.GOING)
        assertTrue("vote:71:GOING" in repo.calls)
        assertEquals(AttendanceAnswer.GOING, vm.state.value.post?.meetup?.myAnswer)
    }

    @Test
    fun `공동 목표 - 지난 주를 읽고 1km 미만은 막고 정하면 닫는다(FR-138)`() {
        val vm = CrewGoalSetViewModel(repo, MutableClock()).also { it.bind(11) }
        assertEquals(LocalDate.of(2026, 10, 19), vm.state.value.weekStart)
        assertTrue(vm.state.value.lastWeek != null)
        vm.onDraft("0.5")
        vm.onSave()
        assertTrue(repo.calls.none { it.startsWith("goal:") })
        vm.onQuick(80)
        vm.onSave()
        assertTrue("goal:80000" in repo.calls)
        assertTrue(vm.state.value.isSaved)
    }
}
