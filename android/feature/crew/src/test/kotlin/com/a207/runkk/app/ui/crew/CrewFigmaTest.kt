package com.a207.runkk.app.ui.crew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRole
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 피그마 F 묶음 대조(S15P31A207-236) 뒤 바꾼 크루 화면. */
@RunWith(RobolectricTestRunner::class)
class CrewFigmaTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()
    private val calls = mutableListOf<String>()

    @Test
    fun `크루런 취소는 서버 사유 문구를 그대로 보이고 일정으로 돌아간다(F-14c)`() {
        val reason = "준비 완료한 참가자가 2명보다 적어 시작하지 않았어요"
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    CrewRunUiState(
                        postId = 31,
                        distance = Distance(5_000),
                        phase = CrewRunPhase.CANCELLED,
                        cancelledReason = reason
                    ),
                    CrewRunActions(onClose = { calls += "close" })
                )
            }
        }
        compose.onNodeWithText(reason).assertExists()
        compose.onNodeWithText("일정으로 돌아가기").performClick()
        assertEquals(listOf("close"), calls)
    }

    @Test
    fun `크루런 취소는 대기실 틀에 0시 0분과 사유, 참가자 준비 상태를 그대로 보이고 나가기도 일정으로 돌아간다(F-14c)`() {
        val reason = "준비 완료한 참가자가 2명보다 적어 시작하지 않았어요"
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    CrewRunUiState(
                        postId = 31,
                        distance = Distance(5_000),
                        phase = CrewRunPhase.CANCELLED,
                        lobby = lobby,
                        untilStart = java.time.Duration.ZERO,
                        cancelledReason = reason
                    ),
                    CrewRunActions(onClose = { calls += "close" }, onLeave = { calls += "leave" })
                )
            }
        }
        compose.onNodeWithText("크루런 · 5km").assertExists()
        compose.onNodeWithText("00:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(reason, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("출발까지", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("준비 완료 1 / 3").assertExists()
        compose.onNodeWithText("준비 완료", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("나가기").performClick()
        compose.onNodeWithText("일정으로 돌아가기").performClick()
        assertEquals(listOf("close", "close"), calls)
    }

    @Test
    fun `크루런 대기실 참가자 격자는 나를 첫 칸에 둔다(F-14)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewRunScreen(
                    CrewRunUiState(postId = 31, distance = Distance(5_000), phase = CrewRunPhase.LOBBY, lobby = lobby),
                    CrewRunActions()
                )
            }
        }
        val me = compose.onNodeWithText("나", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val other = compose.onNodeWithText("바람처럼", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(me.top <= other.top && me.left < other.left, "나는 첫 줄 첫 칸이다")
    }

    @Test
    fun `크루런 그만두기 확인은 계속 달리기와 그만두기 두 알약이다(F-15d)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewQuitDialog(onConfirm = { calls += "quit" }, onDismiss = { calls += "keep" })
            }
        }
        compose.onNodeWithText("크루런을 그만둘까요?").assertExists()
        compose.onNodeWithText("계속 달리기").performClick()
        compose.onNodeWithText("그만두기").performClick()
        assertEquals(listOf("keep", "quit"), calls)
    }

    @Test
    fun `크루 상세를 열면 크루 정보를 읽어 지난 주 달린 거리를 채우고 닫으면 지운다(F-02)`() {
        val repo = FakeCrewRepository()
        val vm = CrewFindViewModel(repo)
        vm.onOpen(crewA)
        assertEquals(11L, vm.state.value.detailInfo?.crewId)
        assertTrue("detail:11" in repo.calls)
        vm.onCloseDetail()
        assertNull(vm.state.value.detailInfo)
    }

    @Test
    fun `크루 상세 통계는 지난 주 달린 거리다(F-02)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewDetailContent(crewA, isJoining = false, onJoin = {}, lastWeekDistance = Distance(64_270))
            }
        }
        compose.onNodeWithText("지난 주 달린 거리").assertExists()
        compose.onNodeWithText("64.27km").assertExists()
    }

    @Test
    fun `크루 찾기 조건 칸을 누르면 평일과 주말 시간대 팝업이 열리고 적용하면 조건에 맞는 크루를 보인다(F-01b, F-01c)`() {
        var state by mutableStateOf(CrewFindUiState(isLoading = false, crews = listOf(crewA)))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewFindScreen(
                    state,
                    CrewFindActions(
                        onConditionOpen = { state = state.copy(isConditionOpen = true, conditionDraft = state.tags) },
                        onConditionToggle = { id ->
                            state = state.copy(conditionDraft = state.conditionDraft + id)
                        },
                        onConditionApply = {
                            state = state.copy(isConditionOpen = false, tags = state.conditionDraft)
                        }
                    )
                )
            }
        }
        compose.onNodeWithText("요일과 시간대를 선택해 조건에 맞는 크루를 찾아보세요").performClick()
        compose.onAllNodesWithText("저녁")[0].performClick()
        compose.onNodeWithText("적용하기").performClick()
        compose.onNodeWithText("조건에 맞는 크루").assertExists()
        assertEquals(setOf("WEEKDAY_EVENING"), state.tags)
        compose.onAllNodesWithText("평일 저녁").assertCountEquals(1)
    }

    @Test
    fun `크루 만들기 태그는 평일과 주말 줄에서 최대 3개까지 고른다(F-04)`() {
        var form by mutableStateOf(CrewFormState(name = "새벽질주"))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewCreateScreen(CrewCreateUiState(form = form), onBack = {}, onForm = { form = it }, onSubmit = {})
            }
        }
        compose.onNodeWithText("태그 최대 3개").assertExists()
        compose.onAllNodesWithText("아침")[0].performClick()
        compose.onAllNodesWithText("점심")[0].performClick()
        compose.onAllNodesWithText("저녁")[1].performClick()
        compose.onAllNodesWithText("새벽")[1].performClick()
        assertEquals(setOf("WEEKDAY_MORNING", "WEEKDAY_LUNCH", "WEEKEND_EVENING"), form.tags)
    }

    @Test
    fun `크루 홈 탭은 레이스, 크루런, 채팅 셋이고 게시판과 크루원 탭이 없다(F-05)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(CrewHomeUiState(crewId = 11, detail = crewDetail(), race = race), CrewHomeActions())
            }
        }
        compose.onNodeWithText("레이스").assertExists()
        compose.onNodeWithText("크루런").assertExists()
        compose.onNodeWithText("채팅").assertExists()
        compose.onNodeWithText("게시판").assertDoesNotExist()
        compose.onNodeWithText("6/10명", substring = true).assertDoesNotExist()
    }

    @Test
    fun `크루 정보 쪽은 태그, 이번 주와 지난 주 거리, 탈퇴하기를 보이고 뒤로 가면 홈이다(F-18)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(
                        crewId = 11,
                        detail = crewDetail().copy(lastWeekDistance = Distance(64_270)),
                        page = CrewHomePage.INFO
                    ),
                    CrewHomeActions(onPage = { calls += "page:$it" }, onLeaveAsk = { calls += "leave" })
                )
            }
        }
        compose.onNodeWithText("새벽질주").assertExists()
        compose.onNodeWithText("평일 저녁").assertExists()
        compose.onNodeWithText("이번 주 달린 거리").assertExists()
        compose.onNodeWithText("64.27km").assertExists()
        compose.onNodeWithText("탈퇴하기").performClick()
        compose.onNodeWithContentDescription("뒤로", substring = true).performClick()
        assertEquals(listOf("leave", "page:HOME"), calls)
    }

    @Test
    fun `크루원 쪽을 처음 열면 크루원을 읽는다(F-22)`() {
        val repo = FakeCrewRepository()
        val vm = CrewHomeViewModel(repo, ServerClock(MutableClock()), FakeCelebrationSharer())
            .also { it.bind(Routes.CrewHome(11)) }
        assertTrue("members" !in repo.calls)
        vm.onPage(CrewHomePage.MEMBERS)
        assertEquals(CrewHomePage.MEMBERS, vm.state.value.page)
        assertTrue("members" in repo.calls)
    }

    @Test
    fun `크루원도 크루 정보에서 초대 코드 팝업을 열고 복사한다(F-18b)`() {
        var state by mutableStateOf(
            CrewHomeUiState(crewId = 11, detail = crewDetail(role = CrewRole.MEMBER), page = CrewHomePage.INFO)
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    state,
                    CrewHomeActions(
                        onInviteOpen = { state = state.copy(isInviteOpen = true) },
                        onInviteCopied = {
                            calls += "copied"
                            state = state.copy(isInviteOpen = false)
                        }
                    )
                )
            }
        }
        compose.onNodeWithText("초대 코드").performClick()
        compose.onNodeWithText("K 7 P 2 Q X").assertExists()
        compose.onNodeWithText("복사").performClick()
        compose.waitForIdle()
        assertEquals(listOf("copied"), calls)
    }

    @Test
    fun `크루 밖이면 초대 코드 단추가 없다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewHomeScreen(
                    CrewHomeUiState(crewId = 11, detail = crewDetail(role = null), page = CrewHomePage.INFO),
                    CrewHomeActions()
                )
            }
        }
        compose.onNodeWithText("탈퇴하기").assertExists()
        compose.onNodeWithText("초대 코드").assertDoesNotExist()
    }
}
