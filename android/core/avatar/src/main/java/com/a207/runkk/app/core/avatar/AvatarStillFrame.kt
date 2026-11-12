package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
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
     * 읽은 아트보드 그림 [frame]에서 몸만 잘라 [maxWidth]×[maxHeight] 안으로 줄인 새 비트맵. 몸이 없으면(아직 그려지지 않은 판) null.
     * [frame]은 여기서 놓는다(recycle).
     */
    fun cut(frame: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap? {
        try {
            val width = frame.width
            val height = frame.height
            val pixels = IntArray(width * height).also { frame.getPixels(it, 0, width, 0, 0, width, height) }
            val bounds = opaqueBounds(pixels, width, height) ?: return null
            val body = Bitmap.createBitmap(frame, bounds.left, bounds.top, bounds.width, bounds.height)
            val size = fitInside(bounds.width, bounds.height, maxWidth, maxHeight)
            if (size.width == body.width && size.height == body.height) return body
            val scaled = Bitmap.createScaledBitmap(body, size.width, size.height, true)
            if (scaled !== body) body.recycle()
            return scaled
        } finally {
            frame.recycle()
        }
    }

    private const val ALPHA_SHIFT = 24
}

/**
 * 전신 정지 그림 캐시 키: 모습(남녀·의상·체형·러닝량)과 칸 크기(px). 생성 머리는 렌더러처럼 참조로 가른다(같은 결과 인스턴스면 같은 머리).
 * 러닝량은 0..100으로 잘라 본다.
 */
internal class AvatarStillKey private constructor(
    private val gender: AvatarGender,
    private val look: AvatarLookKey,
    private val head: GeneratedHeadLayers?,
    private val width: Int,
    private val height: Int
) {
    override fun equals(other: Any?): Boolean = other is AvatarStillKey &&
        gender == other.gender &&
        look == other.look &&
        head === other.head &&
        width == other.width &&
        height == other.height

    override fun hashCode(): Int {
        var result = gender.hashCode()
        result = HASH_PRIME * result + look.hashCode()
        result = HASH_PRIME * result + System.identityHashCode(head)
        result = HASH_PRIME * result + width
        result = HASH_PRIME * result + height
        return result
    }

    companion object {
        private const val HASH_PRIME = 31

        fun of(look: RiveAvatarLook, width: Int, height: Int): AvatarStillKey = AvatarStillKey(
            look.gender,
            AvatarLookKey(
                look.outfit,
                look.bodyShape,
                look.runningAmount.coerceIn(MIN_RUNNING_AMOUNT, MAX_RUNNING_AMOUNT)
            ),
            look.headLayers,
            width,
            height
        )
    }
}

/**
 * 프로세스 공용 전신 정지 그림 캐시. 같은 모습·같은 칸 크기면 다시 그리지 않는다(랭킹을 다시 열거나 기간을 바꿔도 바로 보인다).
 * 그림 바이트 합 [MAX_BYTES]를 넘으면 오래 안 쓴 것부터 버린다(버린 비트맵은 화면이 아직 쓸 수 있어 recycle하지 않는다).
 */
internal object AvatarStillCache {
    private const val MAX_BYTES = 16 * 1024 * 1024

    private val stills = object : LruCache<AvatarStillKey, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: AvatarStillKey, value: Bitmap): Int = value.byteCount
    }

    fun get(key: AvatarStillKey): ImageBitmap? = stills.get(key)?.asImageBitmap()

    fun put(key: AvatarStillKey, still: Bitmap) {
        stills.put(key, still)
    }
}
