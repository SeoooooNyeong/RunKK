package com.a207.runkk.app.ui.notification

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 서버 이벤트 그림(배너·썸네일) 한 장. [load]로 바이트를 받아 [maxPx]에 맞게 줄여 푼다(크루 사진 CrewPhoto와 같은 방식).
 * 받는 동안과 실패하면 아무것도 그리지 않는다(바탕색이 보인다).
 */
@Composable
internal fun EventImage(
    url: String?,
    load: suspend (String) -> Outcome<ByteArray>,
    maxPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap by produceState<ImageBitmap?>(null, url, maxPx) {
        value = null
        val target = url ?: return@produceState
        value = when (val r = load(target)) {
            is Outcome.Success -> withContext(Dispatchers.Default) { decodeEventImage(r.value, maxPx) }
            is Outcome.Failure -> null
        }
    }
    bitmap?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = modifier.fillMaxSize()) }
}

/** 바이트를 [maxPx] 안팎으로 줄여 푼다(2의 거듭제곱 표본). 풀 수 없으면 null. */
internal fun decodeEventImage(bytes: ByteArray, maxPx: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
} catch (e: OutOfMemoryError) {
    Timber.w(e, "이벤트 그림 풀기 실패(메모리)")
    null
} catch (e: IllegalArgumentException) {
    Timber.w(e, "이벤트 그림 풀기 실패")
    null
}

/** 썸네일(84dp)과 배너(358×212dp) 그림을 푸는 최대 변(px). */
internal const val EVENT_THUMB_PX = 256
internal const val EVENT_BANNER_PX = 1_080
