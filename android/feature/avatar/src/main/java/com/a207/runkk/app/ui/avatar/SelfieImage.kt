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

/**
 * 셀카 사진 읽기(실기기 DV3-3): 긴 변이 셀카 한도(1024px) 근처가 되게 줄여 읽고 EXIF 방향을 픽셀에 적용한다. 그래서 사진 확인 화면, 서버로
 * 보내는 JPEG([SelfieEncoder], 방향 태그 없이 픽셀이 바로 섬), 보낸 사진 사본이 모두 바로 선 그림이다. 카메라(TakePicture 파일)와 앨범이
 * 같은 길을 쓴다. 읽지 못하면 null이다.
 */
internal object SelfieImage {
    /** [uri]의 사진을 줄여 읽고 바로 세운다. */
    fun decode(context: Context, uri: Uri): Bitmap? = try {
        val orientation = context.contentResolver.openInputStream(uri)?.use(::orientationOf)
            ?: ExifInterface.ORIENTATION_UNDEFINED
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleOf(bounds) }
        context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?.let { oriented(it, orientation) }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    /** JPEG 바이트를 줄여 읽고 바로 세운다(시험·메모리 안 사진). */
    fun decode(jpeg: ByteArray): Bitmap? {
        val orientation = ByteArrayInputStream(jpeg).use(::orientationOf)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleOf(bounds) }
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)?.let { oriented(it, orientation) }
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
