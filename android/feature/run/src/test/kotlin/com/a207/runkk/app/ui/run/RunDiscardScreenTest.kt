package com.a207.runkk.app.ui.run

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.domain.run.RunProgress
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 자유런 취소 버튼(B2·B2-2)과 확인 대화상자. 잠금 화면(B3)에는 취소가 없다(FR-182). */
@RunWith(RobolectricTestRunner::class)
class RunDiscardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = RunActions(
        onDiscard = { calls += "discard" },
        onDiscardConfirm = { calls += "confirm" },
        onDiscardDismiss = { calls += "dismiss" }
    )

    private fun show(state: RunUiState) = compose.setContent {
        RunkkTheme(reduceMotion = true) { RunScreen(state, actions) }
    }

    @Test
    fun `측정 화면에 48dp 이상 취소 버튼이 있고 누르면 취소 행동을 부른다`() {
        show(RunUiState(phase = RunPhase.RUNNING))
        compose.onNodeWithContentDescription("러닝 취소")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf("discard"), calls)
    }

    @Test
    fun `일시정지 화면에도 취소 버튼이 있다`() {
        show(RunUiState(phase = RunPhase.RUNNING, progress = RunProgress.INITIAL.copy(isPaused = true)))
        compose.onNodeWithContentDescription("재개").assertExists()
        compose.onNodeWithContentDescription("러닝 취소").performClick()
        assertEquals(listOf("discard"), calls)
    }

    @Test
    fun `잠금 화면에는 취소 버튼이 없다`() {
        show(RunUiState(phase = RunPhase.LOCKED, progress = RunProgress.INITIAL.copy(isLocked = true)))
        compose.onNodeWithContentDescription("러닝 취소").assertDoesNotExist()
    }

    @Test
    fun `확인 대화상자는 제목, 한 줄 본문, 계속 달리기와 취소하기를 보인다`() {
        show(RunUiState(phase = RunPhase.RUNNING, isDiscardConfirming = true))
        compose.onNodeWithText("러닝을 취소할까요?").assertExists()
        compose.onNodeWithText("기록이 남지 않아요.").assertExists()
        compose.onNodeWithText("계속 달리기").performClick()
        compose.onNodeWithText("취소하기").performClick()
        assertEquals(listOf("dismiss", "confirm"), calls)
    }

    @Test
    fun `종료 중이거나 잠기면 확인 대화상자를 보이지 않는다`() {
        show(
            RunUiState(
                phase = RunPhase.LOCKED,
                progress = RunProgress.INITIAL.copy(isLocked = true),
                isDiscardConfirming = true
            )
        )
        compose.onNodeWithText("러닝을 취소할까요?").assertDoesNotExist()
    }
}
