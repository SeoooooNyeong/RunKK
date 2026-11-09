package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/** 글꼴·글자 스타일이 피그마(IBM Plex Sans KR, RunKK 글자 스타일)와 같은지(S15P31A207-292). */
class RunkkTypeTest {
    @Test
    fun `본문과 큰 숫자 모두 IBM Plex Sans KR이고 기울이지 않는다`() {
        assertEquals(IbmPlexSansKr, RunkkTextStyles.Body.fontFamily)
        assertEquals(IbmPlexSansKr, RunkkTextStyles.DisplayHuge.fontFamily)
        assertEquals(FontStyle.Normal, RunkkTextStyles.DisplayHuge.fontStyle ?: FontStyle.Normal)
        assertEquals(FontWeight.Bold, RunkkTextStyles.DisplayHuge.fontWeight)
    }

    @Test
    fun `피그마에 없는 굵기(800·900)는 Bold로 맞춘다`() {
        assertEquals(FontWeight.Bold, RunkkTextStyles.text(16, FontWeight.Black).fontWeight)
        assertEquals(FontWeight.Bold, RunkkTextStyles.display(26, FontWeight.ExtraBold).fontWeight)
        assertEquals(FontWeight.SemiBold, RunkkTextStyles.text(15, FontWeight.SemiBold).fontWeight)
    }

    @Test
    fun `글자 스타일 표는 피그마 값이다`() {
        val table = listOf(
            Triple(RunkkTextStyles.TopBarTitle, 19 to FontWeight.Bold, 23),
            Triple(RunkkTextStyles.Title, 20 to FontWeight.Bold, 30),
            Triple(RunkkTextStyles.DialogTitle, 18 to FontWeight.Bold, 26),
            Triple(RunkkTextStyles.SheetTitle, 18 to FontWeight.Bold, 22),
            Triple(RunkkTextStyles.Body, 14 to FontWeight.Normal, 22),
            Triple(RunkkTextStyles.BodyMedium, 14 to FontWeight.Medium, 22),
            Triple(RunkkTextStyles.Caption, 12 to FontWeight.Normal, 18),
            Triple(RunkkTextStyles.Meta, 13 to FontWeight.Normal, 20),
            Triple(RunkkTextStyles.DisplayHuge, 96 to FontWeight.Bold, 72)
        )
        table.forEach { (style, sizeWeight, lineHeight) ->
            assertEquals(sizeWeight.first.sp, style.fontSize)
            assertEquals(sizeWeight.second, style.fontWeight)
            assertEquals(lineHeight.sp, style.lineHeight)
        }
    }

    @Test
    fun `버튼 글자는 크기마다 SemiBold 16·15, Medium 14·12다`() {
        assertEquals(
            listOf(
                16 to FontWeight.SemiBold,
                15 to FontWeight.SemiBold,
                14 to FontWeight.Medium,
                12 to FontWeight.Medium
            ),
            listOf(RunkkButtonSize.Large, RunkkButtonSize.Medium, RunkkButtonSize.Small, RunkkButtonSize.XSmall).map {
                it.textStyle.fontSize.value.toInt() to it.textStyle.fontWeight
            }
        )
    }
}
