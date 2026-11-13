package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

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
