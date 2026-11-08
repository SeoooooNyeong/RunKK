package com.a207.runkk.app.ui.battle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.battle.PlacementRoundState
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 배치고사 진행 카드(FR-279·FR-280, D-520, 피그마 61354:2971): 판 전·판 뒤 머리와 칸 이름, 임시 배지. */
@RunWith(RobolectricTestRunner::class)
class PlacementScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
            RunkkTheme(reduceMotion = true) { content() }
        }
    }

    private fun assertTexts(vararg texts: String) =
        texts.forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists(it) }

    /** 머리 「n」「/3판」은 「배치고사 3판 중 n판째」 한 줄로 읽는다(「/」를 읽지 않게, PL2-A5). 보이는 글자는 기준 이미지가 본다. */
    private fun assertHead(spoken: String) =
        compose.onNodeWithContentDescription(spoken, useUnmergedTree = true).assertExists(spoken)

    /**
     * 칸 이름(「1판 완료」 등)은 머리 낭독과 겹쳐 읽지 않는다(PL2-A5). 보이는 칸 이름은 기준 이미지 P1_placement_round1~3·P2가 본다.
     */
    private fun assertSteps() = listOf("1판 완료", "1판 이번 판", "2판", "3판").forEach {
        compose.onNodeWithText(it, substring = true).assertDoesNotExist()
    }

    @Test
    fun `TC-5877 진행 카드 칸 이름은 완료·이번 판·남은 판이다`() {
        show { PlacementProgressBeforeRound(placing(0)) }
        assertHead("배치고사 3판 중 1판째")
        assertSteps()
        compose.onNodeWithContentDescription("임시", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `TC-5877 한 판을 센 뒤 진행 카드는 다음 판과 임시 배지를 보인다`() {
        show { PlacementProgressBeforeRound(placing(1, "GOLD")) }
        assertHead("배치고사 3판 중 2판째")
        assertSteps()
        compose.onNodeWithContentDescription("임시 골드 티어", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TC-5877 두 판을 센 뒤 진행 카드는 3판 이번 판이다`() {
        show { PlacementProgressBeforeRound(placing(2, "SILVER")) }
        assertHead("배치고사 3판 중 3판째")
        assertSteps()
    }

    @Test
    fun `TC-5878 판 뒤 진행 카드는 센 판 수와 완료를 보인다`() {
        show { PlacementProgressAfterRound(placing(1, "GOLD")) }
        assertHead("배치고사 3판 중 1판 완료")
        assertSteps()
        compose.onNodeWithContentDescription("임시 골드 티어", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TC-5878 두 판을 센 뒤 판 뒤 진행 카드`() {
        show { PlacementProgressAfterRound(placing(2, "SILVER")) }
        assertHead("배치고사 3판 중 2판 완료")
        assertSteps()
        compose.onNodeWithContentDescription("임시 실버 티어", useUnmergedTree = true).assertExists()
    }

    private fun resultState(
        state: PlacementRoundState?,
        isStillPending: Boolean = false,
        progressError: UiText? = null
    ) = PlacementResultUiState(
        isLoading = false,
        battleId = 903,
        result = battleResult().copy(battleId = 903, placementRound = 3, placementState = state),
        state = state,
        progress = if (progressError == null) placing(2, "GOLD") else null,
        isStillPending = isStillPending,
        progressError = progressError
    )

    @Test
    fun `결과를 확인하는 중 줄은 나타나면 읽어 준다(PL2-A4)`() {
        show { PlacementResultScreen(resultState(PlacementRoundState.PENDING), PlacementResultActions()) }
        compose.onNode(
            hasText("결과를 확인하고 있어요") and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        ).assertExists()
    }

    @Test
    fun `30초 뒤에도 정리 중이면 안내 옆 다시 확인으로 다시 읽는다(PL2-A2)`() {
        var retries = 0
        show {
            PlacementResultScreen(
                resultState(PlacementRoundState.PENDING, isStillPending = true),
                PlacementResultActions(onRetry = { retries++ })
            )
        }
        assertTexts("결과를 정리하고 있어요. 잠시 뒤 다시 확인해 주세요")
        compose.onNodeWithText("다시 확인").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `진행을 끝내 못 읽으면 진행 카드 자리에 오류와 다시 시도를 둔다(PL2-A2)`() {
        var retries = 0
        show {
            PlacementResultScreen(
                resultState(PlacementRoundState.COUNTED, progressError = UiText.Res(R.string.placement_load_failed)),
                PlacementResultActions(onRetry = { retries++ })
            )
        }
        assertTexts("배치고사 진행을 불러오지 못했어요")
        compose.onNodeWithContentDescription("판 완료", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("다시 시도").performScrollTo().performClick()
        assertEquals(1, retries)
    }
}
