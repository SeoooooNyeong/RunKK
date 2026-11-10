package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRole
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 와이어프레임 v0.10(E4, E6, E7): 크루 탈퇴와 크루 내 정보 편집. */
class CrewWireframeV010ViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeCrewRepository()

    private fun vm(role: CrewRole): CrewHomeViewModel {
        repo.role = role
        return CrewHomeViewModel(repo, ServerClock(MutableClock()), FakeCelebrationSharer(), { _, _ ->
            Outcome.Success(Unit)
        })
            .also {
                it.bind(Routes.CrewHome(11))
                it.onPage(CrewHomePage.MEMBERS)
            }
    }

    @Test
    fun `크루원이 남은 크루장이 탈퇴를 누르면 확인 대신 양도 안내를 연다(E7, FR-126)`() {
        val vm = vm(CrewRole.LEADER)
        vm.onLeaveAsk()
        assertTrue(vm.state.value.isLeaveBlocked)
        assertFalse(vm.state.value.isConfirmingLeave)
        vm.onLeaveBlockedDismiss()
        assertFalse(vm.state.value.isLeaveBlocked)
    }

    @Test
    fun `크루원은 탈퇴 확인을 연다`() {
        val vm = vm(CrewRole.MEMBER)
        vm.onLeaveAsk()
        assertTrue(vm.state.value.isConfirmingLeave)
        assertFalse(vm.state.value.isLeaveBlocked)
    }

    @Test
    fun `크루원 탭은 줄마다 이번 주 거리를 보이려고 레이스도 읽는다(E4)`() {
        val vm = vm(CrewRole.MEMBER)
        assertTrue("race" in repo.calls)
        assertEquals(race, vm.state.value.race)
    }
}

/** 와이어프레임 v0.10 화면: 탈퇴 불가 안내(E7), 내 정보 편집 시트(E6), 축하 카드(E9), 크루원 메뉴(I10)와 스크린샷. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class CrewWireframeV010ScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private val leaderHome = CrewHomeUiState(
        crewId = 11,
        detail = crewDetail(role = CrewRole.LEADER),
        page = CrewHomePage.INFO,
        race = raceWithGoal,
        ranking = ranking,
        members = members.copy(members = members.members.map { if (it.memberId == 1L) it.copy(isMe = true) else it })
    )

    @Test
    fun `크루장 탈퇴 불가 안내는 닫기 하나다(F-28, W7)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    leaderHome.copy(isLeaveBlocked = true),
                    CrewHomeActions(onLeaveBlockedDismiss = { calls += "dismiss" })
                )
            }
        }
        compose.onNodeWithText("지금은 크루를 탈퇴할 수 없어요").assertExists()
        compose.onNodeWithText("다른 크루원에게 크루장을 넘긴 뒤 탈퇴할 수 있어요.").assertExists()
        compose.onNodeWithText("크루장 넘기기").assertDoesNotExist()
        compose.onNodeWithText("닫기").performClick()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `크루 내 정보 편집은 글자 수를 보이고 저장한다(E6)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    leaderHome.copy(isEditingIntro = true, introDraft = "매일 새벽 5시"),
                    CrewHomeActions(onIntroSave = { calls += "save" })
                )
            }
        }
        compose.onNodeWithText("한 줄 소개 8/40").assertExists()
        compose.onNodeWithText("저장").performClick()
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun membersDark() = compose.captureRunkkScreen("W_E4_crew_members_dark") {
        CrewHomeScreen(leaderHome, CrewHomeActions())
    }

    @Test
    fun membersLight() = compose.captureRunkkScreen("W_E4_crew_members_light", isDarkTheme = false) {
        CrewHomeScreen(leaderHome, CrewHomeActions())
    }

    @Test
    fun goalDoneDark() = compose.captureRunkkScreen("W_E9_crew_goal_done_dark") {
        CrewHomeScreen(leaderHome.copy(tab = CrewTab.RACE), CrewHomeActions())
    }

    @Test
    fun goalDoneLight() = compose.captureRunkkScreen("W_E9_crew_goal_done_light", isDarkTheme = false) {
        CrewHomeScreen(leaderHome.copy(tab = CrewTab.RACE), CrewHomeActions())
    }

    @Test
    fun leaveBlocked() = compose.captureRunkkScreen("W_E7_crew_leave_blocked") {
        CrewHomeScreen(leaderHome.copy(isLeaveBlocked = true), CrewHomeActions())
    }

    @Test
    fun memberMenuDark() = compose.captureRunkkScreen("W_I10_crew_member_menu_dark") {
        CrewHomeScreen(
            leaderHome.copy(page = CrewHomePage.MEMBERS, openedMember = members.members[1]),
            CrewHomeActions()
        )
    }

    @Test
    fun memberMenuLight() = compose.captureRunkkScreen("W_I10_crew_member_menu_light", isDarkTheme = false) {
        CrewHomeScreen(
            leaderHome.copy(page = CrewHomePage.MEMBERS, openedMember = members.members[1]),
            CrewHomeActions()
        )
    }

    @Test
    fun crewEdit() = compose.captureRunkkScreen("W_I9_crew_edit") {
        CrewHomeScreen(
            leaderHome.copy(page = CrewHomePage.EDIT, editForm = crewDetail().toFormState()),
            CrewHomeActions()
        )
    }

    @Test
    fun deleteConfirm() = compose.captureRunkkScreen("W_I11_crew_delete_confirm") {
        CrewHomeScreen(leaderHome.copy(isConfirmingCrewDelete = true), CrewHomeActions())
    }

    @Test
    fun findLight() = compose.captureRunkkScreen("W_E1_crew_find_light", isDarkTheme = false) {
        CrewFindScreen(
            CrewFindUiState(
                isCheckingMine = false,
                isLoading = false,
                tags = setOf("WEEKDAY_EVENING"),
                crews = listOf(crewA, crewB)
            ),
            CrewFindActions()
        )
    }
}
