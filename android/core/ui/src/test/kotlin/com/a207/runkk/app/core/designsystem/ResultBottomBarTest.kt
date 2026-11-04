package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 결과 하단 고정 바(D-502): 두 버튼 모두 터치 영역이 48dp 이상이다(PL1-A2). */
@RunWith(RobolectricTestRunner::class)
class ResultBottomBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `PL1-A2 글자 버튼도 터치 높이 48dp 이상이다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) { ResultBottomBar("다음 판 뛰기", {}, "홈으로", {}) }
        }
        listOf("다음 판 뛰기", "홈으로").forEach { label ->
            val bounds = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
            val height = bounds.bottom - bounds.top
            assertTrue(height >= 48.dp, "$label 높이 $height")
        }
    }
}
