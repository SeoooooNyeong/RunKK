package com.a207.runkk.app.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * 셀카 사진 읽기(실기기 DV3-3): 긴 변이 셀카 한도(1024px) 근처가 되게 줄여 읽고 EXIF 방향을 픽셀에 적용한다. 그래서 사진 확인 화면, 서버로
 * 보내는 JPEG([SelfieEncoder], 방향 태그 없이 픽셀이 바로 섬), 보낸 사진 사본이 모두 바로 선 그림이다. 카메라(TakePicture 파일)와 앨범이
 * 같은 길을 쓴다. 읽지 못하면(깨진 사진, 공급자 오류, 메모리 부족 등 취소를 뺀 모든 예외) null이다.
 */
internal object SelfieImage {
    /** [uri]의 사진을 줄여 읽고 바로 세운다. */
    fun decode(context: Context, uri: Uri): Bitmap? = decode { context.contentResolver.openInputStream(uri) }

    /** JPEG 바이트를 줄여 읽고 바로 세운다(시험·메모리 안 사진). */
    fun decode(jpeg: ByteArray): Bitmap? = decode { ByteArrayInputStream(jpeg) }

    /** [open]이 여는 사진(세 번 연다: 방향·크기·그림)을 줄여 읽고 바로 세운다. */
    fun decode(open: () -> InputStream?): Bitmap? = try {
        val orientation = open()?.use(::orientationOf) ?: ExifInterface.ORIENTATION_UNDEFINED
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleOf(bounds) }
        open()?.use { BitmapFactory.decodeStream(it, null, options) }?.let { oriented(it, orientation) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 앨범·카메라 공급자, BitmapFactory, ExifInterface가 던지는 것(IO·보안·IllegalArgument·런타임)을 모두 「받지 못함」으로 끝낸다
        // (리뷰 RC-1). 이 함수는 화면 범위의 launch에서 불리므로 새면 앱이 멈춘다.
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** EXIF 방향([ExifInterface.ORIENTATION_ROTATE_90] 등)을 픽셀에 적용한다. 정상·모름이면 그대로 돌려준다. */
    fun oriented(source: Bitmap, orientation: Int): Bitmap {
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

            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun orientationOf(input: InputStream): Int = try {
        ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
    } catch (e: IOException) {
        ExifInterface.ORIENTATION_UNDEFINED
    }

    /** 긴 변이 [SelfieEncoder.MAX_SIDE] 근처가 되는 2의 거듭제곱 줄임. */
    private fun sampleOf(bounds: BitmapFactory.Options): Int {
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SelfieEncoder.MAX_SIDE) sample *= 2
        return sample
    }

    private const val DEGREES_90 = 90f
    private const val DEGREES_180 = 180f
    private const val DEGREES_270 = 270f
}
