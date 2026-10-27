package com.a207.runkk.app.api.crew

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 게시글 사진을 서버 한도에 맞게 줄인다(6.1.14 api.crew, FR-211, D-100, D-142, NFR-042).
 *
 * 고른 원본(장당 10MB 이하 JPEG·PNG)만 받는다. 10MB를 넘으면 Rejected(TOO_LARGE), 0바이트·JPEG·PNG가 아닌 머리·풀 수 없는 그림은
 * Rejected(UNSUPPORTED_IMAGE)이고 예외를 던지지 않는다(사유 코드는 서버 ReasonCode 이름을 쓴다).
 * 순서(D-142 (1)): 원본을 비트맵으로 풀어 EXIF 방향(Orientation)을 픽셀에 먼저 반영 → 긴 변 1600px(작으면 키우지 않음)·JPEG 품질 85로
 * 인코딩 → 1MB를 넘으면 품질을 5씩 낮추되 하한 50 → 그래도 넘으면 긴 변을 0.8배씩 줄여 다시 품질 85부터.
 * 새 JPEG는 비트맵에서 다시 만들므로 원본 메타데이터(EXIF GPS·XMP 등)가 없다. 원본 메타데이터를 새 파일에 복사하지 않는다.
 */
internal class PhotoCompressor internal constructor(
    private val read: (String) -> ByteArray?,
    private val codec: Codec
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ uri -> readLimited(context, uri) }, AndroidCodec)

    /** 비트맵 풀기·인코딩(시험은 가짜로 시도 순서를 기록한다). */
    internal interface Codec {
        /** 원본을 풀어 EXIF 방향을 픽셀에 반영한 그림. 풀 수 없으면 null. */
        fun decode(original: ByteArray): Image?

        /** 긴 변을 [longEdge]px로 맞춰 JPEG 품질 [quality]로 인코딩한다. */
        fun encode(image: Image, longEdge: Int, quality: Int): ByteArray

        /** 다 쓴 그림의 메모리를 돌려준다. */
        fun release(image: Image) = Unit
    }

    /** 방향을 반영한 그림과 그 크기. [scaled]는 코덱이 마지막으로 줄인 그림(긴 변, 그림)을 담아 품질 시도마다 다시 쓴다. */
    internal class Image(val width: Int, val height: Int, val pixels: Any) {
        var scaled: Pair<Int, Any>? = null
    }

    /** 기기 사진 주소(content://…)를 읽어 줄인 JPEG를 돌려준다. */
    fun compress(uri: String): Outcome<ByteArray> {
        val original = read(uri) ?: return Outcome.Failure(AppError.Rejected(UNSUPPORTED_IMAGE))
        return compress(original)
    }

    fun compress(original: ByteArray): Outcome<ByteArray> {
        if (original.size > MAX_ORIGINAL_BYTES) return Outcome.Failure(AppError.Rejected(TOO_LARGE))
        if (!isJpeg(original) && !isPng(original)) return Outcome.Failure(AppError.Rejected(UNSUPPORTED_IMAGE))
        val image =
            try {
                codec.decode(original)
            } catch (e: OutOfMemoryError) {
                return Outcome.Failure(AppError.Rejected(TOO_LARGE))
            } ?: return Outcome.Failure(AppError.Rejected(UNSUPPORTED_IMAGE))
        return try {
            encodeWithinLimit(image)?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Rejected(TOO_LARGE))
        } catch (e: OutOfMemoryError) {
            Outcome.Failure(AppError.Rejected(TOO_LARGE))
        } finally {
            codec.release(image)
        }
    }

    /**
     * 긴 변을 0.8배씩 줄이며 1MB 이하가 되는 첫 결과. 처음 긴 변에서는 반드시 한 번 시도한다(긴 변 64px 미만인 작은 그림도 인코딩한다).
     * 64px 아래로 줄여야 하면 null.
     */
    private fun encodeWithinLimit(image: Image): ByteArray? {
        var longEdge = minOf(MAX_LONG_EDGE, max(image.width, image.height))
        var result: ByteArray?
        do {
            result = encodeAtBestQuality(image, longEdge)
            longEdge = (longEdge * SHRINK).roundToInt()
        } while (result == null && longEdge >= MIN_LONG_EDGE)
        return result
    }

    /** 품질 85에서 5씩 하한 50까지 낮추며 1MB 이하가 되는 첫 인코딩. 없으면 null. */
    private fun encodeAtBestQuality(image: Image, longEdge: Int): ByteArray? =
        (START_QUALITY downTo MIN_QUALITY step QUALITY_STEP)
            .asSequence()
            .map { quality -> codec.encode(image, longEdge, quality) }
            .firstOrNull { it.size <= MAX_RESULT_BYTES }

    internal companion object {
        const val TOO_LARGE = "TOO_LARGE"
        const val UNSUPPORTED_IMAGE = "UNSUPPORTED_IMAGE"

        /** 원본 상한 10MB(10,485,760B), 결과 상한 1MB(1,048,576B, 서버 한도 D-100). */
        const val MAX_ORIGINAL_BYTES = 10 * 1024 * 1024
        const val MAX_RESULT_BYTES = 1024 * 1024
        const val MAX_LONG_EDGE = 1600
        const val START_QUALITY = 85
        const val MIN_QUALITY = 50
        const val QUALITY_STEP = 5
        const val SHRINK = 0.8

        /** 줄여 가다 이보다 작아져야 하면 포기한다(정상 사진에서는 오지 않는다). 처음부터 이보다 작은 그림은 그 크기로 한 번 시도한다. */
        const val MIN_LONG_EDGE = 64

        fun isJpeg(bytes: ByteArray): Boolean = bytes.size >= JPEG_MAGIC.size &&
            JPEG_MAGIC.indices.all { bytes[it] == JPEG_MAGIC[it] }

        fun isPng(bytes: ByteArray): Boolean = bytes.size >= PNG_MAGIC.size &&
            PNG_MAGIC.indices.all { bytes[it] == PNG_MAGIC[it] }

        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

        /** 원본을 읽되 상한 + 1바이트까지만 읽는다(넘으면 TOO_LARGE로 거부하므로 더 읽지 않는다). */
        private fun readLimited(context: Context, uri: String): ByteArray? = try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER)
                var total = 0
                while (total <= MAX_ORIGINAL_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    total += n
                }
                out.toByteArray()
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }

        private const val BUFFER = 64 * 1024
    }

    /**
     * Android 비트맵 구현. 큰 원본은 긴 변이 1600px 이상으로 남는 만큼만 표본을 줄여 푼다(메모리). 줄인 비트맵은 긴 변마다 한 번만
     * 만들어 품질 시도에 다시 쓰고, 다 쓴 중간 비트맵은 recycle한다. 투명한 PNG는 흰 바탕에 그려 JPEG로 바꾼다(검은 바탕 방지).
     */
    private object AndroidCodec : Codec {

        override fun decode(original: ByteArray): Image? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_LONG_EDGE) sample *= 2
            val decoded =
                BitmapFactory.decodeByteArray(
                    original,
                    0,
                    original.size,
                    BitmapFactory.Options().apply {
                        inSampleSize =
                            sample
                    }
                )
                    ?: return null
            val rotated = rotate(decoded, orientationOf(original))
            if (rotated !== decoded) decoded.recycle()
            val opaque = if (rotated.hasAlpha()) onWhite(rotated).also { rotated.recycle() } else rotated
            return Image(opaque.width, opaque.height, opaque)
        }

        override fun release(image: Image) {
            image.scaled?.let { (_, scaled) -> if (scaled !== image.pixels) (scaled as Bitmap).recycle() }
            image.scaled = null
            (image.pixels as Bitmap).recycle()
        }

        private fun onWhite(bitmap: Bitmap): Bitmap {
            val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(opaque).apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            return opaque
        }

        override fun encode(image: Image, longEdge: Int, quality: Int): ByteArray {
            val scaled = scaled(image, longEdge)
            return ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        }

        /** 긴 변을 맞춘 비트맵. 같은 긴 변이면 지난번 것을 다시 쓰고, 바뀌면 지난번 것을 recycle한다(그림마다 따로 둔다). */
        private fun scaled(image: Image, longEdge: Int): Bitmap {
            val bitmap = image.pixels as Bitmap
            image.scaled?.let { (edge, cached) -> if (edge == longEdge) return cached as Bitmap }
            image.scaled?.let { (_, old) -> if (old !== bitmap) (old as Bitmap).recycle() }
            val scale = longEdge.toDouble() / max(bitmap.width, bitmap.height)
            val result =
                if (scale >= 1.0) {
                    bitmap
                } else {
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                        true
                    )
                }
            image.scaled = longEdge to result
            return result
        }

        /** EXIF 방향을 읽기만 한다(PNG·EXIF 없음은 보통 방향). */
        private fun orientationOf(original: ByteArray): Int = try {
            ExifInterface(ByteArrayInputStream(original))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

        private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(DEGREES_90)

                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(DEGREES_180)

                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(DEGREES_270)

                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)

                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)

                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(DEGREES_90)
                    matrix.postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(DEGREES_270)
                    matrix.postScale(-1f, 1f)
                }

                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        private const val DEGREES_90 = 90f
        private const val DEGREES_180 = 180f
        private const val DEGREES_270 = 270f
    }
}
