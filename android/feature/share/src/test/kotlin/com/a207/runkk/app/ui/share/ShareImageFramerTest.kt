package com.a207.runkk.app.ui.share

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 공유 이미지 틀: 카드 아래에만 워드마크를 붙인다(사용자 결정 2026-11-09, K-2·K-3). */
@RunWith(RobolectricTestRunner::class)
class ShareImageFramerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val framer = ShareImageFramer(context)

    private fun card(width: Int = 334, height: Int = 383): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    @Test
    fun `카드는 테마 바탕 여백 안에 그대로 있고 그 아래에 워드마크 칸이 붙는다`() {
        val source = card()
        val framed = framer.frame(CapturedCard(source, isDark = false))
        val pad = (source.width * ShareImageFramer.PAD_RATIO).roundToInt()
        assertEquals(source.width + 2 * pad, framed.width)
        assertTrue(framed.height > source.height + 2 * pad, "워드마크 칸 ${framed.height}")
        assertEquals(Color.RED, framed.getPixel(pad + source.width / 2, pad + source.height / 2), "카드 가운데")
        assertEquals(ShareImageFramer.backgroundOf(isDark = false), framed.getPixel(1, 1), "왼쪽 위 바탕")
    }

    @Test
    fun `워드마크는 카드 아래 가운데에 그려지고 카드 너비의 오분의 일쯤이다`() {
        val source = card()
        val framed = framer.frame(CapturedCard(source, isDark = true))
        val pad = (source.width * ShareImageFramer.PAD_RATIO).roundToInt()
        val markTop = pad + source.height
        val background = ShareImageFramer.backgroundOf(isDark = true)
        val inked = mutableListOf<Int>()
        for (y in markTop until framed.height) {
            for (x in 0 until framed.width) {
                if (framed.getPixel(x, y) != background) inked += x
            }
        }
        assertTrue(inked.isNotEmpty(), "워드마크 픽셀")
        val left = inked.min()
        val right = inked.max()
        val center = (left + right) / 2f
        assertEquals(framed.width / 2f, center, framed.width * 0.03f, "가운데")
        assertTrue(right - left <= source.width * ShareImageFramer.MARK_WIDTH_RATIO + 2, "작게 ${right - left}")
    }

    @Test
    fun `라이트는 검은 글자 워드마크, 다크는 라임 워드마크와 어두운 바탕이다`() {
        assertTrue(
            ShareImageFramer.wordmarkRes(isDark = false) != ShareImageFramer.wordmarkRes(isDark = true),
            "테마별 그림"
        )
        assertTrue(ShareImageFramer.backgroundOf(isDark = true) != ShareImageFramer.backgroundOf(isDark = false))
    }
}
