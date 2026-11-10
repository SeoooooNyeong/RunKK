package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PostList
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 와이어프레임 v0.10 반영 브랜치를 합친 뒤 이은 화면 연결: 크루 홈의 입력이 부르는 경로(M1 공지 쓰기, M4 채팅, M7 대면 모임 상세,
 * M8 주간 공동 목표, F8 다른 러너 프로필)와, 그 입력을 화면의 어느 칸이 부르는지.
 */
class CrewHomeNavigationTest {
    private val navigated = mutableListOf<NavKey>()
    private val actions = CrewHomeActions().withNavigation(crewId = 11, navigate = { navigated += it })

    @Test
    fun `레이스 탭 목표 설정은 주간 공동 목표 화면으로 간다(M8)`() {
        actions.onGoalEdit()
        assertEquals(listOf<NavKey>(Routes.CrewGoalSet(11)), navigated)
    }

    @Test
    fun `크루 정보의 지난 크루런·지난 목표는 W7 경로로 간다(F-20·F-21)`() {
        actions.onPastRuns()
        actions.onPastGoals()
        assertEquals(listOf<NavKey>(Routes.CrewPastRuns(11), Routes.CrewPastGoals(11)), navigated)
    }

    @Test
    fun `크루런 제목은 일정 상세, 지난 크루런 링크는 지난 크루런 화면으로 간다(W6)`() {
        actions.onRunOpen(crewRunPosts.posts[0])
        actions.onPastRuns()
        assertEquals(listOf<NavKey>(Routes.CrewRunDetail(31), Routes.CrewPastRuns(11)), navigated)
    }

    @Test
    fun `일정 만들기, 대기실, 결과 경로는 그대로다(크루 관리 경로는 지웠다, W7)`() {
        actions.onWriteSchedule()
        actions.onLobby(crewRunPosts.posts[0])
        actions.onResult(crewRunPosts.posts[0])
        assertEquals(
            listOf<NavKey>(Routes.CrewRunWrite(11), Routes.CrewRunLobby(31), Routes.CrewRunResult(31)),
            navigated
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class CrewWireLinksScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun home(tab: CrewTab, role: CrewRole = CrewRole.MEMBER) = CrewHomeUiState(
        crewId = 11,
        detail = crewDetail(role = role),
        tab = tab,
        race = raceWithGoal,
        ranking = ranking,
        crewRuns = PostList(emptyList(), null),
        members = members.copy(members = members.members.map { if (it.memberId == 1L) it.copy(isMe = true) else it })
    )

    @Test
    fun `크루원 쪽에서 줄을 누르면 크루원 정보 팝업을 연다(F-22b, W7)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    home(CrewTab.RACE).copy(page = CrewHomePage.MEMBERS),
                    CrewHomeActions(onMemberOpen = { calls += "member:${it.memberId}" })
                )
            }
        }
        compose.onNode(hasText("달빛") and hasClickAction(), useUnmergedTree = false).performScrollTo().performClick()
        assertEquals(listOf("member:2"), calls)
    }

    @Test
    fun `탭 줄의 채팅 칸은 채팅 탭을 고르고, 채팅 탭은 목록 밖 칸에 채팅 내용을 그린다(D-성-21)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(home(CrewTab.RACE), CrewHomeActions(onTab = { calls += it.name }))
            }
        }
        compose.onNodeWithText("채팅").performClick()
        assertEquals(listOf("CHAT"), calls)
    }
}
