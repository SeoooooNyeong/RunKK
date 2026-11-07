package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 피그마 F 묶음 대조(S15P31A207-236) 뒤 바꾼 크루 화면. */
@RunWith(RobolectricTestRunner::class)
class CrewFigmaTest {
    @get:Rule
    val compose = createComposeRule()

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
}
