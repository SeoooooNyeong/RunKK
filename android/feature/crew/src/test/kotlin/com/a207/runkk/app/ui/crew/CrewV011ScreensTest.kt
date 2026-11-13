package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PollMethod
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.11 크루 화면: 권한표 버튼 노출(3.0.3), 고정 공지, 공동 목표·기여도·축하 카드, 대면 모임, 투표, 게시판, 운영진 지정. */
@RunWith(RobolectricTestRunner::class)
class CrewV011ScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun home(role: CrewRole, tab: CrewTab = CrewTab.RACE) = CrewHomeUiState(
        crewId = 11,
        detail = crewDetail(role = role),
        tab = tab,
        race = raceWithGoal,
        ranking = ranking
    )

    @Test
    fun `⋯ 메뉴는 역할과 무관하게 크루 정보와 크루원 두 줄이고 운영진은 목표 설정만 더 본다(F-17·F-24, W7)`() {
        var role by mutableStateOf(CrewRole.MEMBER)
        var isMenuOpen by mutableStateOf(false)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(role).copy(race = raceWithGoal.copy(celebration = null), isMenuOpen = isMenuOpen),
                    CrewHomeActions(
                        onPage = {
                            calls += "page:$it"
                            isMenuOpen = false
                        },
                        onMenuOpen = { isMenuOpen = true }
                    )
                )
            }
        }
        compose.onNodeWithText("목표 정하기").assertDoesNotExist()
        compose.onNodeWithContentDescription("크루 메뉴").performClick()
        compose.onNodeWithText("크루원").assertExists()
        compose.onNodeWithText("크루 관리").assertDoesNotExist()
        compose.onNodeWithText("크루 정보").performClick()
        role = CrewRole.MANAGER
        compose.waitForIdle()
        compose.onNodeWithText("목표 정하기").assertExists()
        compose.onNodeWithContentDescription("크루 메뉴").performClick()
        compose.onNodeWithText("크루 관리").assertDoesNotExist()
        compose.onNodeWithText("크루원").performClick()
        assertEquals(listOf("page:INFO", "page:MEMBERS"), calls)
    }

    @Test
    fun `레이스 - 공동 목표 카드는 합계, 목표, 진행률을 읽고 목표 설정으로 간다(와이어 E2)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(CrewRole.LEADER).copy(race = raceWithGoal.copy(celebration = null)),
                    CrewHomeActions(onGoalEdit = { calls += "goal" })
                )
            }
        }
        compose.onNodeWithContentDescription("공동 목표 50.0킬로미터 중 58.6킬로미터, 100퍼센트").assertExists()
        compose.onNodeWithText("목표 정하기").performClick()
        assertEquals(listOf("goal"), calls)
    }

    @Test
    fun `레이스 - 목표가 없는 주는 이번 주 달린 거리로 읽히고 마감은 없다(F-06)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(CrewRole.LEADER).copy(race = race.copy(contributors = raceWithGoal.contributors)),
                    CrewHomeActions()
                )
            }
        }
        compose.onNodeWithText("이번 주 달린 거리").assertExists()
        compose.onNodeWithText("이번 주 목표").assertDoesNotExist()
        compose.onNodeWithText("일요일 자정 마감").assertDoesNotExist()
        compose.onNodeWithText("목표 정하기").assertExists()
    }

    @Test
    fun `레이스 - 달성한 주는 달성 띠와 목표 카드, 러닝 보드는 이번 주만 순위 숫자 없이 읽는다(피그마 F-08, FR-140·FR-141)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(CrewRole.LEADER),
                    CrewHomeActions(
                        onCelebrationOpen = { calls += "celebration" },
                        onGoalEdit = { calls += "goal" },
                        onTab = { calls += it.name }
                    )
                )
            }
        }
        compose.onNodeWithText("목표 달성", substring = true).assertExists()
        compose.onNodeWithText("달성").assertExists()
        compose.onNodeWithText("이번 주").assertExists()
        compose.onNodeWithContentDescription("달빛 27.5킬로미터").assertExists()
        compose.onNodeWithContentDescription("나 12.9킬로미터").assertExists()
        compose.onNodeWithText("자세히 보기").performClick()
        compose.onNodeWithText("목표 정하기").performClick()
        compose.onNodeWithText("채팅").performClick()
        assertEquals(listOf("celebration", "goal", "CHAT"), calls)
    }
}
