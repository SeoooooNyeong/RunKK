package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewCelebrationStore
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 레이스 응답만 바꾼 가짜 저장소(공용 FakeCrewRepository를 고치지 않는다). */
private class RaceRepository(private val base: FakeCrewRepository, private val answer: RaceStatus) :
    CrewRepository by base {
    override suspend fun race(crewId: Long, weekStart: LocalDate?): Outcome<RaceStatus> {
        base.calls += "race"
        return Outcome.Success(answer)
    }
}

/** 이번 주 목표 80km를 105% 달성한 레이스(피그마 F-08). 마감은 10.25(일) 24시, 내 기여 30%. */
private val achieved = raceWithGoal.copy(
    total = Distance(84_230),
    goal = Distance(80_000),
    goalProgressPercent = 105,
    myShare = 30.0
)

/** 피그마 맞춤 W5 크루 홈(S15P31A207-296): 채팅 탭, 진행률 100% 초과, 참여 인원·내 기여, 축하 팝업 그 주 한 번. */
class CrewHomeW5ViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val base = FakeCrewRepository()
    private val store = CrewCelebrationStore.inMemory()

    private fun vm(key: Routes.CrewHome = Routes.CrewHome(11), race: RaceStatus = achieved) = CrewHomeViewModel(
        RaceRepository(base, race),
        ServerClock(MutableClock()),
        FakeCelebrationSharer(),
        store
    ).also { it.bind(key) }

    @Test
    fun `채팅 알림 경로(tab CHAT)로 열면 채팅 탭이고 레이스·크루런을 읽지 않는다(D-성-21)`() {
        val vm = vm(Routes.CrewHome(11, "CHAT"))
        assertEquals(CrewTab.CHAT, vm.state.value.tab)
        assertTrue("race" !in base.calls)
        assertTrue(base.calls.none { it.startsWith("posts:") })
        vm.onTab(CrewTab.RACE)
        assertTrue("race" in base.calls)
        assertEquals(CrewTab.RACE, CrewTab.of(null))
        assertEquals(CrewTab.RACE, CrewTab.of("UNKNOWN"))
    }

    @Test
    fun `목표를 달성한 주는 첫 진입 때 축하 팝업을 한 번 띄우고 같은 주 다시 들어오면 띄우지 않는다(F-08b, D-성-23)`() {
        val first = vm()
        assertNotNull(first.state.value.celebration)
        assertTrue(first.state.value.isCelebrationBannerShown)
        assertTrue(store.isSeen(11, LocalDate.of(2026, 10, 19)))
        first.onCelebrationClose()
        assertNull(first.state.value.celebration)
        first.onCelebrationOpen()
        assertNotNull(first.state.value.celebration, "달성 띠의 자세히 보기로 다시 연다")

        val again = vm()
        assertNull(again.state.value.celebration)
        assertTrue(again.state.value.isCelebrationBannerShown)
    }

    @Test
    fun `달성하지 않은 주는 팝업도 띠도 없다`() {
        val vm = vm(race = achieved.copy(achievedAt = null, celebration = null, goalProgressPercent = 80))
        assertNull(vm.state.value.celebration)
        assertFalse(vm.state.value.isCelebrationBannerShown)
    }

    @Test
    fun `진행률은 100을 넘겨도 그대로, 막대는 100%에서 꽉 차고 내 기여 구간은 그 비율이다(C2, W5-D2)`() {
        assertEquals(105, goalProgressLabel(achieved))
        assertEquals(1f, goalFillFraction(achieved))
        assertEquals(0.3f, myFillFraction(achieved), 0.0001f)
        assertEquals(30, myShareLabel(achieved))
        assertEquals(1, myShareLabel(achieved.copy(myShare = 1.0)), "서버 1%를 100%로 읽지 않는다")
        val noGoal = achieved.copy(goal = null, goalProgressPercent = null)
        assertNull(goalProgressLabel(noGoal))
        assertEquals(1f, goalFillFraction(noGoal), "목표가 없어도 막대를 꽉 채운다(F-06)")
    }

    @Test
    fun `참여 인원은 거리가 0보다 큰 기여자 줄 수이고 주의 월요일은 마감에서 셈한다(W5-D1)`() {
        val withZero = achieved.copy(
            contributors = achieved.contributors + RaceStatus.Contributor("쉼표", Distance(0), 0.0, memberId = 9)
        )
        assertEquals(3, participantCount(withZero))
        assertEquals(LocalDate.of(2026, 10, 19), raceWeekStart(achieved, java.time.ZoneId.of("Asia/Seoul")))
        assertEquals(10 to 4, weekOfMonth(LocalDate.of(2026, 10, 19)))
    }
}

/** 레이스 탭·축하 팝업 화면(피그마 F-05·F-06·F-08·F-08b)과 스크린샷. */
@RunWith(RobolectricTestRunner::class)
class CrewHomeW5ScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun home(race: RaceStatus, role: CrewRole = CrewRole.LEADER) = CrewHomeUiState(
        crewId = 11,
        detail = crewDetail(role = role),
        race = race,
        ranking = ranking
    )

    @Test
    fun `목표 카드는 이번 주 목표, 105%, 참여 인원과 내 기여를 보이고 크루장은 목표 정하기를 누른다(F-05)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(achieved.copy(achievedAt = null, celebration = null)),
                    CrewHomeActions(onGoalEdit = {
                        calls +=
                            "goal"
                    })
                )
            }
        }
        compose.onNodeWithText("이번 주 목표").assertExists()
        compose.onNodeWithText("일요일 자정 마감").assertExists()
        compose.onNodeWithText("러닝 보드").assertExists()
        compose.onNodeWithText("목표 정하기").performClick()
        assertEquals(listOf("goal"), calls)
    }

    @Test
    fun `목표가 없는 주의 크루원은 목표 정하기가 없다(F-06b)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(
                        achieved.copy(goal = null, goalProgressPercent = null, achievedAt = null, celebration = null),
                        CrewRole.MEMBER
                    ),
                    CrewHomeActions()
                )
            }
        }
        compose.onNodeWithText("이번 주 달린 거리").assertExists()
        compose.onNodeWithText("목표 정하기").assertDoesNotExist()
        compose.onNodeWithText("이번 주 공동 목표가 없어요").assertDoesNotExist()
    }

    @Test
    fun `축하 팝업의 확인은 닫는다(F-08b)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(achieved).copy(celebration = achieved),
                    CrewHomeActions(onCelebrationClose = { calls += "close" })
                )
            }
        }
        compose.onNodeWithText("함께 목표를 달성했어요", substring = true).assertExists()
        compose.onNodeWithText("확인").performClick()
        assertEquals(listOf("close"), calls)
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun `레이스 달성 주 스크린샷 라이트`() = compose.captureRunkkScreen("W5_crew_race_achieved_light", isDarkTheme = false) {
        CrewHomeScreen(home(achieved), CrewHomeActions(), isAtTabRoot = true)
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun `레이스 목표 없음 스크린샷 다크`() = compose.captureRunkkScreen("W5_crew_race_no_goal_dark", isDarkTheme = true) {
        CrewHomeScreen(
            home(achieved.copy(goal = null, goalProgressPercent = null, achievedAt = null, celebration = null)),
            CrewHomeActions(),
            isAtTabRoot = true
        )
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun `축하 팝업 스크린샷 라이트`() = compose.captureRunkkScreen("W5_crew_celebration_light", isDarkTheme = false) {
        CrewHomeScreen(home(achieved).copy(celebration = achieved), CrewHomeActions(), isAtTabRoot = true)
    }
}
