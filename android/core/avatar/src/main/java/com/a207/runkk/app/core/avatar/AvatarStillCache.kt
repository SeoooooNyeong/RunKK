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
 * 같은 모습을 다른 칸 크기로 읽어 둔 그림이 이 칸에 그대로 줄여 맞는 것이면 그것을 쓴다(기록런 VS 칸 → 카운트다운 VS 칸, S15P31A207-335 8회차).
 * 그림 바이트 합 [MAX_BYTES]를 넘으면 오래 안 쓴 것부터 버린다(버린 비트맵은 화면이 아직 쓸 수 있어 recycle하지 않는다).
 */
internal object AvatarStillCache {
    private const val MAX_BYTES = 16 * 1024 * 1024

    private val stills = object : LruCache<AvatarStillKey, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: AvatarStillKey, value: Bitmap): Int = value.byteCount
    }

    /**
     * [key] 칸의 그림. 그 칸 크기로 읽은 것이 없으면 같은 모습의 다른 칸 그림 가운데 이 칸에 맞는 것([AvatarStillFrame.standsIn]),
     * 여럿이면 가장 작은 것이다. 없으면 null(부르는 쪽이 Rive로 새로 읽는다).
     */
    fun get(key: AvatarStillKey): ImageBitmap? {
        stills.get(key)?.let { return it.asImageBitmap() }
        val borrowed = stills.snapshot().entries
            .filter { (other, still) ->
                other.isSameLook(key) && AvatarStillFrame.standsIn(still.width, still.height, key.width, key.height)
            }
            .minByOrNull { (_, still) -> still.width * still.height } ?: return null
        // 빌려 쓴 그림도 최근에 쓴 것으로 올린다
        return stills.get(borrowed.key)?.asImageBitmap()
    }

    fun put(key: AvatarStillKey, still: Bitmap) {
        stills.put(key, still)
    }
}
