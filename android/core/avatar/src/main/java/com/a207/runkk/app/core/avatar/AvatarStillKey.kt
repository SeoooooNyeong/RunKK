package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 전신 정지 그림 캐시 키: 모습(남녀·의상·체형·러닝량)과 칸 크기(px). 생성 머리는 렌더러처럼 참조로 가른다(같은 결과 인스턴스면 같은 머리).
 * 러닝량은 0..100으로 잘라 본다.
 */
internal class AvatarStillKey private constructor(
    private val gender: AvatarGender,
    private val look: AvatarLookKey,
    private val head: GeneratedHeadLayers?,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean = other is AvatarStillKey &&
        isSameLook(other) &&
        width == other.width &&
        height == other.height

    /** 칸 크기를 빼고 같은 모습인가(다른 칸 크기로 읽은 그림을 빌려 쓸 때, [AvatarStillCache.get]). */
    fun isSameLook(other: AvatarStillKey): Boolean = gender == other.gender && look == other.look && head === other.head

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
