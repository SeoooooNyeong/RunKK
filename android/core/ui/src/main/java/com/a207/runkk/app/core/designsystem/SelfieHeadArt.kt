package com.a207.runkk.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart

/**
 * 읽은 셀카 머리 층(그리는 순서)과 구도. [face]는 얼굴 층(목을 잇는 층)이다. [frame]은 프로필 원 안 구도, [freeFrame]은 원 밖 구도다
 * ([PortraitHeadFrame]). 층은 그릴 크기에 맞춰 줄여 읽은 것이고 구도도 그 크기에 맞춘 값이다.
 */
internal class SelfieHeadArt(
    val layers: List<ImageBitmap>,
    val face: ImageBitmap?,
    val frame: PortraitHeadFrame?,
    val freeFrame: PortraitHeadFrame?
) {
    /** 원본 캔버스에서 잰 구도(층 내용마다 한 번). 구도를 정하지 못했으면 두 값이 null이다. */
    class Frames(val width: Int, val height: Int, val frame: PortraitHeadFrame?, val freeFrame: PortraitHeadFrame?)

    companion object {
        /** 층 내용별 구도(작은 값, 랭킹·크루처럼 여러 머리를 오가도 다시 재지 않는다). */
        private val frames = SelfieHeadArtCache<Frames>(FRAME_CACHE_SIZE)

        /**
         * 그릴 크기에 맞춰 줄여 읽은 머리(층 내용 + 줄인 배수로 찾는다). 목록의 작은 얼굴(40dp 안팎)은 원본 1024 대신 256 안팎으로 읽어
         * 머리 하나가 약 0.8MB다(원본이면 12MB). 바이트 합 상한으로 담는다.
         */
        private val arts = SelfieHeadArtCache<SelfieHeadArt>(ART_CACHE_SIZE, ART_CACHE_BYTES) { art ->
            art.layers.sumOf { it.width.toLong() * it.height * BYTES_PER_PIXEL }
        }

        private const val FRAME_CACHE_SIZE = 32
        private const val ART_CACHE_SIZE = 24
        private const val ART_CACHE_BYTES = 24L * 1024 * 1024
        private const val BYTES_PER_PIXEL = 4
        private const val MAX_SAMPLE = 8

        /**
         * 층을 원본 크기로 한 번 읽어 구도를 잰다(내용이 같으면 담아 둔 것). 그림 하나라도 읽지 못하면 null이라 부르는 쪽이 기본 머리를
         * 그린다. 구성(composition)에서 불러 그릴 수 있는지 먼저 가린다.
         */
        fun framesOf(layers: List<FaceLayer>): Frames? = frames.getOrLoad(layers) { analyze(it) }

        /**
         * [sidePx](상자 한 변, 화면 픽셀)에 그릴 머리. 원본 1px이 화면 1px보다 작게 그려지는 만큼 2의 거듭제곱으로 줄여 읽는다(화면에서
         * 줄인 그림 1px이 1px을 넘지 않아 선명함은 같다). 읽지 못하면 null.
         */
        fun of(layers: List<FaceLayer>, sidePx: Int): SelfieHeadArt? {
            val measured = framesOf(layers) ?: return null
            val sample = sampleFor(measured, sidePx)
            return arts.getOrLoad(layers, sample) { decode(it, measured, sample) }
        }

        /** 원본 1px이 화면에서 차지하는 크기로 줄일 배수(1, 2, 4, 8)를 고른다. */
        internal fun sampleFor(frames: Frames, sidePx: Int): Int {
            val scale = frames.frame?.scale ?: (1f / maxOf(frames.width, frames.height))
            val screenPerSource = scale * sidePx
            if (screenPerSource <= 0f) return 1
            var sample = 1
            while (sample < MAX_SAMPLE && sample * 2 * screenPerSource <= 1f) sample *= 2
            return sample
        }

        /** 원본 크기로 읽어 구도를 잰다. 얼굴·헤어 층이 같은 크기일 때만 잰다. 읽은 비트맵은 바로 놓는다. */
        private fun analyze(layers: List<FaceLayer>): Frames? {
            val bounds = layers.map { boundsOf(it) ?: return null }
            val face = layers.firstOrNull { it.part == FacePart.EARS }
            val hair = layers.firstOrNull { it.part == FacePart.HAIR }
            val width = bounds.maxOf { it.first }
            val height = bounds.maxOf { it.second }
            if (face == null || hair == null) return Frames(width, height, null, null)
            val faceBitmap = decodeFull(face) ?: return null
            val hairBitmap = decodeFull(hair) ?: return null
            try {
                if (faceBitmap.width != hairBitmap.width || faceBitmap.height != hairBitmap.height) {
                    return Frames(width, height, null, null)
                }
                val facePixels = pixels(faceBitmap)
                val hairPixels = pixels(hairBitmap)
                val w = faceBitmap.width
                val h = faceBitmap.height
                return Frames(
                    w,
                    h,
                    PortraitHeadFrame.of(facePixels, hairPixels, w, h, isInCircle = true),
                    PortraitHeadFrame.of(facePixels, hairPixels, w, h, isInCircle = false)
                )
            } finally {
                faceBitmap.recycle()
                hairBitmap.recycle()
            }
        }

        /** [sample]배 줄여 읽고 구도를 그 크기로 옮긴다. */
        private fun decode(layers: List<FaceLayer>, measured: Frames, sample: Int): SelfieHeadArt? {
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmaps = layers.map { BitmapFactory.decodeByteArray(it.png, 0, it.png.size, options) ?: return null }
            val faceIndex = layers.indexOfFirst { it.part == FacePart.EARS }.takeIf { it >= 0 }
            // 줄인 배수는 디코더가 정한 실제 크기로 잰다(원본이 배수로 나누어떨어지지 않아도 맞게).
            val factor = faceIndex?.let { measured.width.toFloat() / bitmaps[it].width } ?: sample.toFloat()
            val images = bitmaps.map { it.asImageBitmap() }
            return SelfieHeadArt(
                images,
                faceIndex?.let(images::get),
                measured.frame?.scaledBy(factor),
                measured.freeFrame?.scaledBy(factor)
            )
        }

        private fun boundsOf(layer: FaceLayer): Pair<Int, Int>? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(layer.png, 0, layer.png.size, options)
            return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
        }

        private fun decodeFull(layer: FaceLayer): Bitmap? = BitmapFactory.decodeByteArray(layer.png, 0, layer.png.size)

        private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }
}
