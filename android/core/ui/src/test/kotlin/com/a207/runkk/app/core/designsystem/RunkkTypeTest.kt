package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/** 글꼴은 prod와 같고(Noto Sans KR·Barlow Condensed 기울임, 2026-11-12 PM 결정) 글자 크기·굵기는 피그마 RunKK 글자 스타일인지. */
class RunkkTypeTest {
    @Test
    fun `본문은 Noto Sans KR이고 큰 숫자는 Barlow Condensed 기울임 800이다`() {
        assertEquals(NotoSansKr, RunkkTextStyles.Body.fontFamily)
        assertEquals(BarlowCondensed, RunkkTextStyles.DisplayHuge.fontFamily)
        assertEquals(FontStyle.Italic, RunkkTextStyles.DisplayHuge.fontStyle)
        assertEquals(FontWeight.ExtraBold, RunkkTextStyles.DisplayHuge.fontWeight)
    }

    @Test
    fun `굵기는 넘긴 값 그대로다(가변 글꼴)`() {
        assertEquals(FontWeight.Black, RunkkTextStyles.text(16, FontWeight.Black).fontWeight)
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
            Triple(RunkkTextStyles.DisplayHuge, 96 to FontWeight.ExtraBold, 72)
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
