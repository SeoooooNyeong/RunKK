package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 셀카를 보내기 전에 줄인다(D-23, D-59): 긴 변 1024px, JPEG, 1MB 이하. 원본은 어디에도 저장하지 않는다(FR-156).
 * 화질을 낮춰도 1MB를 넘으면 마지막 결과를 돌려주고 서버가 거부한다.
 */
internal object SelfieEncoder {
    const val MAX_SIDE = 1_024
    const val MAX_BYTES = 1_000_000

    fun encode(source: Bitmap): ByteArray {
        val longest = max(source.width, source.height)
        val scaled = if (longest > MAX_SIDE) {
            val ratio = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(
                source,
                (source.width * ratio).roundToInt().coerceAtLeast(1),
                (source.height * ratio).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }
        var quality = START_QUALITY
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= QUALITY_STEP
        } while (bytes.size > MAX_BYTES && quality >= MIN_QUALITY)
        if (scaled !== source) scaled.recycle()
        return bytes
    }

    private const val START_QUALITY = 90
    private const val QUALITY_STEP = 10
    private const val MIN_QUALITY = 40
}
