package com.a207.runkk.app.ui.crew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
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
}
