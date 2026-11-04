package com.a207.runkk.app.ui.battle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
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
        CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.of(true)) {
            RunkkTheme(reduceMotion = true) { content() }
        }
    }

    private fun assertTexts(vararg texts: String) =
        texts.forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists(it) }

    /** 머리 「n」「/3판」은 「배치고사 n/3판」 한 줄로 읽는다(보이는 글자는 기준 이미지가 본다). */
    private fun assertHead(spoken: String) =
        compose.onNodeWithContentDescription(spoken, useUnmergedTree = true).assertExists(spoken)

    private fun assertAbsent(vararg texts: String) =
        texts.forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }

    @Test
    fun `TC-5877 진행 카드 칸 이름은 완료·이번 판·남은 판이다`() {
        show { PlacementProgressBeforeRound(placing(0)) }
        assertHead("배치고사 1/3판")
        assertTexts("1판 이번 판", "2판", "3판")
        assertAbsent("1판 완료")
        compose.onNodeWithContentDescription("임시", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `TC-5877 한 판을 센 뒤 진행 카드는 다음 판과 임시 배지를 보인다`() {
        show { PlacementProgressBeforeRound(placing(1, "GOLD")) }
        assertHead("배치고사 2/3판")
        assertTexts("1판 완료", "2판 이번 판", "3판")
        compose.onNodeWithContentDescription("임시 골드 티어", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TC-5877 두 판을 센 뒤 진행 카드는 3판 이번 판이다`() {
        show { PlacementProgressBeforeRound(placing(2, "SILVER")) }
        assertHead("배치고사 3/3판")
        assertTexts("1판 완료", "2판 완료", "3판 이번 판")
    }

    @Test
    fun `TC-5878 판 뒤 진행 카드는 센 판 수와 완료를 보인다`() {
        show { PlacementProgressAfterRound(placing(1, "GOLD")) }
        assertHead("배치고사 1/3판 완료")
        assertTexts("1판 완료", "2판", "3판")
        assertAbsent("2판 이번 판")
        compose.onNodeWithContentDescription("임시 골드 티어", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TC-5878 두 판을 센 뒤 판 뒤 진행 카드`() {
        show { PlacementProgressAfterRound(placing(2, "SILVER")) }
        assertHead("배치고사 2/3판 완료")
        assertTexts("1판 완료", "2판 완료", "3판")
        compose.onNodeWithContentDescription("임시 실버 티어", useUnmergedTree = true).assertExists()
    }
}
