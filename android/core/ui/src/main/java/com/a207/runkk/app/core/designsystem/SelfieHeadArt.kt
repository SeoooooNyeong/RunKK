package com.a207.runkk.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart

/** 읽은 셀카 머리 층(그리는 순서)과 구도. [face]는 얼굴 층(목을 잇는 층)이다. */
internal class SelfieHeadArt(val layers: List<ImageBitmap>, val face: ImageBitmap?, val frame: PortraitHeadFrame?) {
    companion object {
        /** 하나라도 읽지 못하면 null. 얼굴·헤어 층이 같은 크기면 구도를 잰다. */
        fun of(layers: List<FaceLayer>): SelfieHeadArt? {
            val sorted = layers.sortedBy { it.part.ordinal }
            val bitmaps = sorted.map { BitmapFactory.decodeByteArray(it.png, 0, it.png.size) ?: return null }
            val face = sorted.indexOfFirst { it.part == FacePart.EARS }.takeIf { it >= 0 }
            val hair = sorted.indexOfFirst { it.part == FacePart.HAIR }.takeIf { it >= 0 }
            val frame = if (face != null && hair != null) frameOf(bitmaps[face], bitmaps[hair]) else null
            val images = bitmaps.map { it.asImageBitmap() }
            return SelfieHeadArt(images, face?.let(images::get), frame)
        }

        private fun frameOf(face: Bitmap, hair: Bitmap): PortraitHeadFrame? {
            if (face.width != hair.width || face.height != hair.height) return null
            return PortraitHeadFrame.of(pixels(face), pixels(hair), face.width, face.height)
        }

        private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }
}
