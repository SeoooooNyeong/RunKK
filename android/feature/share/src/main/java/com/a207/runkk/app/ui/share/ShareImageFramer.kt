package com.a207.runkk.app.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.a207.runkk.app.core.designsystem.RunkkColors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.roundToInt
import com.a207.runkk.app.core.designsystem.R as DesignR

/**
 * 공유 이미지 틀(K-2·K-3, 사용자 결정 2026-11-09): 찍은 카드를 화면 테마 바탕 위에 놓고 카드 아래 가운데에 런크크 워드마크를 작게 붙인다.
 * 워드마크는 화면 미리보기에는 없고 저장·스토리·다른 앱으로 나가는 이미지에만 있다. 그림은 로그인 화면과 같은 한글 워드마크이고
 * 라이트는 검은 글자, 다크는 라임 글자 그림이다(RunkkLogo와 같은 고르기).
 */
class ShareImageFramer @Inject constructor(@ApplicationContext private val context: Context) {
    fun frame(card: CapturedCard): Bitmap {
        val source = card.bitmap
        val pad = (source.width * PAD_RATIO).roundToInt()
        val gap = (source.width * GAP_RATIO).roundToInt()
        val markWidth = (source.width * MARK_WIDTH_RATIO).roundToInt()
        val mark = ContextCompat.getDrawable(context, wordmarkRes(card.isDark))
        val markHeight = mark?.takeIf { it.intrinsicWidth > 0 }?.let {
            (markWidth * it.intrinsicHeight / it.intrinsicWidth.toFloat()).roundToInt()
        } ?: 0
        val width = source.width + 2 * pad
        val height = pad + source.height + gap + markHeight + pad
        val framed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(framed)
        canvas.drawColor(backgroundOf(card.isDark))
        canvas.drawBitmap(source, pad.toFloat(), pad.toFloat(), null)
        mark?.let {
            val left = (width - markWidth) / 2
            val top = pad + source.height + gap
            it.setBounds(left, top, left + markWidth, top + markHeight)
            it.draw(canvas)
        }
        return framed
    }

    companion object {
        /** 카드 둘레 여백(카드 너비 대비). */
        internal const val PAD_RATIO = 0.06f

        /** 카드와 워드마크 사이(카드 너비 대비). */
        internal const val GAP_RATIO = 0.04f

        /** 워드마크 너비(카드 너비 대비). */
        internal const val MARK_WIDTH_RATIO = 0.2f

        internal fun wordmarkRes(isDark: Boolean): Int =
            if (isDark) DesignR.drawable.logo_wordmark_ko_lime else DesignR.drawable.logo_wordmark_ko_dark

        internal fun backgroundOf(isDark: Boolean): Int =
            (if (isDark) RunkkColors.dark else RunkkColors.light).background.toArgb()
    }
}
