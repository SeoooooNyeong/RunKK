package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 전신 정지 그림의 픽셀 처리([RiveAvatarStill], S15P31A207-300). Rive 서기를 한 번 그려 읽은 아트보드 그림에서 몸을 감싸는 사각형만
 * 잘라 칸 크기 안으로 줄인다. 부르는 쪽은 주 스레드가 아닌 곳에서 부른다(픽셀을 한 번 훑는다).
 */
internal object AvatarStillFrame {
    /** 이 값 이하의 알파는 몸으로 보지 않는다(가장자리 흐림·거의 투명한 합성 잔여). */
    private const val MIN_ALPHA = 8

    /** 왼쪽 위 포함, 오른쪽 아래 제외 사각형(px). */
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    data class Size(val width: Int, val height: Int)

    /** [pixels](ARGB, 가로 [width])에서 알파가 [MIN_ALPHA]를 넘는 픽셀을 감싸는 가장 작은 사각형. 다 비었으면 null. */
    fun opaqueBounds(pixels: IntArray, width: Int, height: Int): Bounds? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (pixels[row + x] ushr ALPHA_SHIFT > MIN_ALPHA) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        return if (right < 0) null else Bounds(left, top, right + 1, bottom + 1)
    }

    /** [width]×[height]를 비율을 지켜 [maxWidth]×[maxHeight] 안에 맞춘 크기. 이미 작으면 그대로(키우지 않는다). 한 변은 1 이상. */
    fun fitInside(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Size {
        val scale = minOf(1f, maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        return Size(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
    }

    /**
     * 자를 틀: 몸 둘레([bounds])와 상관없이 아트보드 크기([frameWidth]×[frameHeight])와 칸 비율로만 정한 같은 크기 틀을 발(몸 아래 끝)에
     * 붙이고 아트보드 가운데에 놓는다. 그래서 남녀가 같은 배율로 줄고 Rive 몸의 키 차이가 그대로 보인다(2026-11-12 PM 요구: 몸 둘레로
     * 자르면 머리가 넓은 여자가 좁은 칸에서 더 작아져 키가 들쭉날쭉했다). 틀이 아트보드 밖으로 나가면 그 자리는 비운다.
     */
    fun window(bounds: Bounds, frameWidth: Int, frameHeight: Int, maxWidth: Int, maxHeight: Int): Bounds {
        val height = (frameHeight * FIGURE_SHARE).roundToInt()
        val width = max((height * MIN_WIDTH_RATIO).roundToInt(), (height.toFloat() * maxWidth / maxHeight).roundToInt())
        val left = (frameWidth - width) / 2
        return Bounds(left, bounds.bottom - height, left + width, bounds.bottom)
    }

    /**
     * 읽은 아트보드 그림 [frame]에서 [window] 틀로 잘라 [maxWidth]×[maxHeight] 안으로 줄인 새 비트맵. 몸이 없으면(아직 그려지지 않은 판)
     * null. [frame]은 여기서 놓는다(recycle).
     */
    fun cut(frame: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap? {
        try {
            val width = frame.width
            val height = frame.height
            val pixels = IntArray(width * height).also { frame.getPixels(it, 0, width, 0, 0, width, height) }
            val bounds = window(opaqueBounds(pixels, width, height) ?: return null, width, height, maxWidth, maxHeight)
            val body = Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888)
            Canvas(body).drawBitmap(frame, -bounds.left.toFloat(), -bounds.top.toFloat(), null)
            val size = fitInside(bounds.width, bounds.height, maxWidth, maxHeight)
            if (size.width == body.width && size.height == body.height) return body
            val scaled = Bitmap.createScaledBitmap(body, size.width, size.height, true)
            if (scaled !== body) body.recycle()
            return scaled
        } finally {
            frame.recycle()
        }
    }

    /**
     * 다른 칸 크기로 읽어 둔 그림([width]×[height])을 [slotWidth]×[slotHeight] 칸에 그대로 써도 되는가(S15P31A207-335 8회차: 기록런 VS 칸
     * 148 그림을 카운트다운 VS 칸 96에 쓴다). 틀([window])은 칸 비율로만 정해지고 가장 좁은 틀도 몸 전체를 담으므로, 그림 비율이 이 칸의 틀
     * 비율보다 넓지 않으면 칸에 비율대로 줄여 발을 바닥에 붙였을 때 몸 크기·자리가 이 칸에서 새로 읽은 그림과 같다. 넓은 그림은 좁은 칸에서
     * 몸이 작아지고, 늘려야 하는 작은 그림은 흐려져서 쓰지 않는다.
     */
    fun standsIn(width: Int, height: Int, slotWidth: Int, slotHeight: Int): Boolean {
        if (width <= 0 || height <= 0 || slotWidth <= 0 || slotHeight <= 0) return false
        val frameRatio = max(MIN_WIDTH_RATIO, slotWidth.toFloat() / slotHeight)
        val scale = minOf(slotWidth.toFloat() / width, slotHeight.toFloat() / height)
        return width.toFloat() / height <= frameRatio + RATIO_TOLERANCE && scale <= 1f + SCALE_TOLERANCE
    }

    /** 반올림으로 생기는 비율·배율 차이(틀·줄이기는 px로 반올림한다). */
    private const val RATIO_TOLERANCE = 0.02f
    private const val SCALE_TOLERANCE = 0.02f

    private const val ALPHA_SHIFT = 24

    /** 틀 높이 = 아트보드 높이 × 이 값(서기 몸이 아트보드의 약 0.6이고, 가장 큰 머리까지 들어가게 조금 더 둔다). */
    private const val FIGURE_SHARE = 0.64f

    /** 틀 너비의 아래 한계(틀 높이 대비). 머리가 넓은 여자 몸(약 0.63)도 잘리지 않게 한다. */
    private const val MIN_WIDTH_RATIO = 0.66f
}
