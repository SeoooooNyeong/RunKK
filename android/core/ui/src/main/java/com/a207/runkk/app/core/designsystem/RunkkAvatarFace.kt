package com.a207.runkk.app.core.designsystem

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 셀카 아바타 얼굴: 부위 PNG(귀·눈·코·입·머리)를 FacePart 순서대로 겹쳐 그린다. 그림 하나라도 읽지 못하면(깨진 PNG)
 * [fallback](지금의 자리 그림)을 그린다. 장식이므로 읽기 정보는 부르는 쪽(아바타 그림 전체)이 붙인다.
 */
@Composable
fun RunkkAvatarFace(layers: List<FaceLayer>, modifier: Modifier = Modifier, fallback: @Composable () -> Unit = {}) {
    val bitmaps: List<ImageBitmap>? = remember(layers) { decodeAll(layers) }
    Box(modifier) {
        if (bitmaps.isNullOrEmpty()) {
            fallback()
        } else {
            bitmaps.forEach { bitmap ->
                Image(
                    bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/** 부위 순서대로 그림을 읽는다. 하나라도 읽지 못하면 null. */
private fun decodeAll(layers: List<FaceLayer>): List<ImageBitmap>? = layers
    .sortedBy { it.part.ordinal }
    .map { layer -> BitmapFactory.decodeByteArray(layer.png, 0, layer.png.size)?.asImageBitmap() ?: return null }
