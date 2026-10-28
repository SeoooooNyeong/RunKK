package com.a207.runkk.app.ui.crew

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 사진 한 장. [load]로 바이트를 받아 [maxPx]에 맞게 줄여 푼다(BitmapFactory inSampleSize). 받는 동안은 빈 칸, 실패하면 안내 문구.
 * [onClick]이 있으면 눌러 크게 본다.
 */
@Composable
internal fun CrewPhoto(
    photoId: Long,
    load: suspend (Long) -> Outcome<ByteArray>,
    description: String,
    modifier: Modifier = Modifier,
    maxPx: Int = THUMB_PX,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val image by produceState<Pair<ImageBitmap?, Boolean>>(null to false, photoId, maxPx) {
        // 다른 사진으로 바뀌면 앞 사진을 지운다. 받기·풀기는 동시에 몇 장만(메모리), 풀다 실패하면(OOM 포함) 안내로 바꾼다.
        value = null to false
        value = PHOTO_GATE.withPermit {
            when (val r = load(photoId)) {
                is Outcome.Success -> withContext(Dispatchers.Default) { decodeOrNull(r.value, maxPx) }.let {
                    it to
                        (it == null)
                }

                is Outcome.Failure -> null to true
            }
        }
    }
    val (bitmap, failed) = image
    Box(
        modifier
            .clip(RunkkShapes.Small)
            .background(colors.surface)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )

            failed -> Text(
                stringResource(R.string.crew_photo_failed),
                style = RunkkTextStyles.Micro,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

/** 사진 크게 보기(전체 화면 대화상자). 닫기 단추와 뒤로가기로 닫는다. */
@Composable
internal fun PhotoViewer(photoId: Long, actions: CrewBoardActions) {
    Dialog(onDismissRequest = actions.onClosePhoto, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pane = stringResource(R.string.crew_photo_viewer_description)
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .semantics { paneTitle = pane },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunkkPillButton(stringResource(R.string.crew_photo_viewer_close), onClick = actions.onClosePhoto)
            CrewPhoto(
                photoId = photoId,
                load = actions.loadPhoto,
                description = stringResource(R.string.crew_photo_viewer_description),
                maxPx = VIEWER_PX,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

/** 바이트를 [maxPx] 안팎으로 줄여 푼다(2의 거듭제곱 표본). 풀 수 없으면 null. */
internal fun decodeOrNull(bytes: ByteArray, maxPx: Int): ImageBitmap? = try {
    decode(bytes, maxPx)
} catch (e: OutOfMemoryError) {
    Timber.w(e, "크루 사진 풀기 실패(메모리)")
    null
} catch (e: IllegalArgumentException) {
    Timber.w(e, "크루 사진 풀기 실패")
    null
}

/** 동시에 받고 푸는 사진 수(메모리 상한). */
private val PHOTO_GATE = Semaphore(MAX_CONCURRENT_PHOTOS)

private fun decode(bytes: ByteArray, maxPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    // 썸네일은 RGB_565로 풀어 메모리를 반으로 줄인다(사진에 투명도가 없다).
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        if (maxPx <= THUMB_PX) inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private const val MAX_CONCURRENT_PHOTOS = 3
internal const val THUMB_PX = 400
private const val VIEWER_PX = 1_600
