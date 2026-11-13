package com.a207.runkk.app.core.avatar

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 서버 목 메타데이터(mobile.neckMask)가 없을 때 얼굴 레이어 알파만으로 목·턱을 찾는 대체 경로(PoC GeneratedHeadGeometry).
 * 픽셀 배열만 다루므로 JVM에서 시험한다.
 */
internal object GeneratedHeadGeometry {
    /** 목 중심 x, 턱(목이 넓어지기 시작하는 줄) y, 불투명 마지막 줄 다음 y. */
    data class Neck(val centerX: Float, val chinY: Float, val bottom: Int)

    private data class Span(val left: Int, val right: Int) {
        val width get() = right - left + 1
        val center get() = (left + right) / 2f
    }

    private const val OPAQUE = 200
    private const val SKIN_OPAQUE = 240
    private const val NOT_FOUND = "neck not found"

    fun detect(pixels: IntArray, width: Int, height: Int): Neck {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixel buffer does not match size" }
        val minRun = maxOf(8, width / 100)
        val rows = Array(height) { y -> widestRun(pixels, width, y, minRun) }
        val bottom = rows.indexOfLast { it != null } + 1
        require(bottom > 0) { NOT_FOUND }
        val maxWidth = rows.filterNotNull().maxOf { it.width }
        val referenceY = bottom - maxOf(8, (maxWidth * .025f).roundToInt())
        val reference = rows.getOrNull(referenceY)
        require(reference != null && reference.width < maxWidth * .55f) { NOT_FOUND }
        val limit = maxOf(0, bottom - maxOf(60, (maxWidth * .45f).roundToInt()))
        var widerRows = 0
        var firstWider = -1
        for (y in referenceY downTo limit) {
            val row = rows[y]
            val wider = row != null && row.width > reference.width * 1.45f &&
                row.width - reference.width > minRun && abs(row.center - reference.center) < maxWidth * .25f
            if (wider) {
                if (widerRows == 0) firstWider = y
                widerRows++
                if (widerRows >= 3) {
                    require(referenceY - firstWider >= maxOf(3, height / 200)) { NOT_FOUND }
                    return Neck(reference.center, (firstWider + 1).toFloat(), bottom)
                }
            } else {
                widerRows = 0
                firstWider = -1
            }
        }
        throw IllegalArgumentException(NOT_FOUND)
    }

    /** 턱 바로 위 목 중심 둘레에서 완전히 불투명한 픽셀의 채널별 중앙값(불투명 ARGB). */
    fun skinColor(pixels: IntArray, width: Int, height: Int, centerX: Float, chinY: Float): Int {
        require(width > 0 && height > 0 && pixels.size == width * height && centerX.isFinite() && chinY.isFinite()) {
            "pixel buffer does not match size"
        }
        val radius = maxOf(4, width / 64)
        val cx = centerX.roundToInt().coerceIn(0, width - 1)
        val cy = chinY.roundToInt().coerceIn(0, height - 1)
        val samples = ArrayList<Int>()
        for (y in (cy - height / 25).coerceAtLeast(0)..(cy - height / 64).coerceIn(0, height - 1)) {
            for (x in (cx - radius).coerceAtLeast(0)..(cx + radius).coerceAtMost(width - 1)) {
                val pixel = pixels[y * width + x]
                if (pixel.ushr(24) >= SKIN_OPAQUE) samples.add(pixel)
            }
        }
        require(samples.size >= 9) { "face skin color not found" }
        fun median(shift: Int) = samples.map { it.ushr(shift) and 255 }.sorted()[samples.size / 2]
        return (255 shl 24) or (median(16) shl 16) or (median(8) shl 8) or median(0)
    }

    private fun widestRun(pixels: IntArray, width: Int, y: Int, minRun: Int): Span? {
        var best: Span? = null
        var start = -1
        for (x in 0..width) {
            val opaque = x < width && pixels[y * width + x].ushr(24) >= OPAQUE
            if (opaque && start < 0) start = x
            if (!opaque && start >= 0) {
                if (x - start >= minRun && x - start > (best?.width ?: 0)) best = Span(start, x - 1)
                start = -1
            }
        }
        return best
    }
}
