package com.a207.runkk.app.ui.crew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.crew.MyCrew
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 초대 링크로 연 크루 가입 확인의 네 결과([임시:FR-성-38], [임시:D-성-244]): 정상, 만료, 이미 구성원, 횟수 초과. 로그인 전은 app이
 * 로그인 뒤에 이 화면을 연다(RunkkNavHostTest).
 */
@RunWith(RobolectricTestRunner::class)
class CrewInviteJoinViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()
    private val repo = FakeCrewRepository()

    private fun vm(code: String = "AB12CD") = CrewInviteJoinViewModel(repo).also { it.load(code) }

    @Test
    fun `정상이면_Found`() {
        val vm = vm()

        assertEquals(CrewInviteJoinUiState.Found(invitePreview), vm.state.value)
        assertEquals(listOf("preview:AB12CD", "myCrew"), repo.calls, "미리보기는 가입시키지 않는다")
    }

    @Test
    fun `다른_크루_소속이어도_미리보기는_Found이고_가입_거부_사유를_보인다`() {
        repo.mine = MyCrew(crewDetail(crewId = 11), emptyList())
        repo.fail["invite"] = AppError.Rejected("ALREADY_IN_CREW")
        val vm = vm()

        vm.onJoin()

        val found = assertIs<CrewInviteJoinUiState.Found>(vm.state.value)
        assertEquals(false, found.isJoining)
        assertEquals(uiText(R.string.crew_error_already_in_crew), found.message)
    }

    @Test
    fun `만료면_Expired와_크루찾기_이동`() = runTest {
        repo.fail["preview"] = AppError.Rejected("INVALID_CODE")
        val vm = vm()

        assertEquals(CrewInviteJoinUiState.Expired, vm.state.value)
        vm.events.test {
            vm.onFindCrews()
            assertEquals(CrewInviteJoinEvent.OpenSearch, awaitItem())
        }
    }

    @Test
    fun `이미_구성원이면_AlreadyMember로_크루홈`() = runTest {
        repo.mine = MyCrew(crewDetail(crewId = invitePreview.crewId), emptyList())
        val vm = vm()

        assertEquals(CrewInviteJoinUiState.AlreadyMember(invitePreview.crewId), vm.state.value)
        vm.events.test { assertEquals(CrewInviteJoinEvent.OpenCrewHome(invitePreview.crewId), awaitItem()) }
        assertTrue(repo.calls.none { it.startsWith("invite:") }, "다시 가입시키지 않는다")
    }

    @Test
    fun `횟수초과는_Limited`() {
        repo.fail["preview"] = AppError.Rejected("TOO_MANY_ATTEMPTS")

        assertEquals(CrewInviteJoinUiState.Limited, vm().state.value)
    }

    @Test
    fun `가입하면_크루홈`() = runTest {
        val vm = vm()

        vm.events.test {
            vm.onJoin()
            assertEquals(CrewInviteJoinEvent.OpenCrewHome(12), awaitItem())
        }
        assertTrue("invite:AB12CD" in repo.calls)
    }

    @Test
    fun `가입하는_사이_코드가_만료되면_Expired다`() {
        val vm = vm()
        repo.fail["invite"] = AppError.Rejected("INVALID_CODE")

        vm.onJoin()

        assertEquals(CrewInviteJoinUiState.Expired, vm.state.value)
    }

    @Test
    fun `연결_실패는_Failed이고_다시_시도하면_읽는다`() {
        repo.fail["preview"] = AppError.Network
        val vm = vm()
        assertIs<CrewInviteJoinUiState.Failed>(vm.state.value)

        repo.fail.remove("preview")
        vm.onRetry()

        assertEquals(CrewInviteJoinUiState.Found(invitePreview), vm.state.value)
    }

    @Test
    fun `같은_코드로_다시_열어도_한_번만_읽는다`() {
        val vm = vm()

        vm.load("AB12CD")

        assertEquals(1, repo.calls.count { it == "preview:AB12CD" })
    }

    @Test
    fun `화면은_정상이면_크루와_가입하기를_만료면_안내와_크루_찾기를_보인다`() {
        val calls = mutableListOf<String>()
        var state by mutableStateOf<CrewInviteJoinUiState>(CrewInviteJoinUiState.Found(invitePreview))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewInviteJoinScreen(
                    state,
                    onBack = {},
                    onJoin = { calls += "join" },
                    onFindCrews = { calls += "find" },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText(invitePreview.name).assertExists()
        compose.onNodeWithText("가입하기").performClick()

        state = CrewInviteJoinUiState.Expired
        compose.onNodeWithText("만료된 초대예요. 크루원에게 새 초대를 받아 주세요.").assertExists()
        compose.onNodeWithText("크루 찾기").performClick()

        state = CrewInviteJoinUiState.Limited
        compose.onNodeWithText("초대 코드를 너무 많이 틀렸어요. 10분 뒤 다시 시도해 주세요").assertExists()

        assertEquals(listOf("join", "find"), calls)
    }
}
