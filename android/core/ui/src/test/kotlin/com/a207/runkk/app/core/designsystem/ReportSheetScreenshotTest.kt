package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 신고 시트(와이어프레임 M10): 사유 다섯 가지, 대상 한 줄, 글자 수, 취소·신고하기와 라이트·다크 스크린샷(NFR-041). */
@RunWith(RobolectricTestRunner::class)
class ReportSheetScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Sheet(state: ReportDialogState, callbacks: ReportSheetCallbacks = ReportSheetCallbacks()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            RunkkReportSheetContent(
                reportDialogTexts(title = "이 글을 신고할까요?", detailLabel = "신고 내용", subtitle = "달빛님의 게시글"),
                state,
                callbacks,
                Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RunkkShapes.Large)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 24.dp)
            )
        }
    }

    @Test
    fun `사유는 가운뎃점 없이 읽고 고르면 알린다, 사유가 없으면 신고하기가 꺼진다`() {
        val picked = mutableListOf<Int>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Sheet(
                    ReportDialogState(null, "", isDetailMissing = false, isSubmitEnabled = false),
                    ReportSheetCallbacks(onSelect = {
                        picked +=
                            it
                    })
                )
            }
        }
        compose.onNodeWithText("광고, 스팸").performClick()
        compose.onNodeWithText("욕설, 비방").assertExists()
        compose.onNodeWithText("0/200").assertExists()
        compose.onNodeWithText("신고하기").assertIsNotEnabled()
        assertEquals(listOf(2), picked)
    }

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun sheetDark() = capture(isDark = true)

    @Config(qualifiers = RUNKK_PHONE)
    @Test
    fun sheetLight() = capture(isDark = false)

    private fun capture(isDark: Boolean) =
        compose.captureRunkkScreen("M10_report_sheet_${if (isDark) "dark" else "light"}", isDarkTheme = isDark) {
            Sheet(ReportDialogState(4, "", isDetailMissing = true, isSubmitEnabled = false))
        }
}
