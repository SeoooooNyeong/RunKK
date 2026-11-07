package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.junit4.createComposeRule
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
}
