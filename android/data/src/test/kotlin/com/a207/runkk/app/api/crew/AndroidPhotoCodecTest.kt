package com.a207.runkk.app.api.crew

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.model.Outcome
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * PhotoCompressor의 실제 비트맵 경로(TC-6512·TC-6515·TC-6516, D-142, NFR-042): Robolectric 네이티브 그래픽으로 JPEG를 실제로 풀고
 * 다시 인코딩한다. 위치가 든 EXIF 원본을 줄인 결과에 EXIF(APP1)·GPS가 없고 방향은 픽셀에 반영돼 있어야 한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidPhotoCodecTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val compressor = PhotoCompressor(context)

    @Test
    fun `TC-6515·TC-6516 EXIF 방향 6과 GPS가 든 원본은 회전을 픽셀에 반영하고 메타데이터 없이 줄인다`() {
        val original = jpegWithExif(width = 2_000, height = 1_500, orientation = ExifInterface.ORIENTATION_ROTATE_90)

        val result = (compressor.compress(original) as Outcome.Success).value

        val bounds = boundsOf(result)
        assertEquals(1_200 to 1_600, bounds, "가로 사진을 90도 돌려 세로 1200×1600")
        assertTrue(result.size <= 1_048_576)
        assertTrue(!containsExifSegment(result), "APP1 Exif 세그먼트 0")
        val exif = ExifInterface(ByteArrayInputStream(result))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE), "GPS 태그 0")
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        assertTrue(
            orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED
        )
    }

    @Test
    fun `TC-6512 세로 PNG는 JPEG로 바꾸고 긴 변 1600에 비율을 지킨다`() {
        val bitmap = Bitmap.createBitmap(1_500, 2_000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val png = java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

        val result = (compressor.compress(png) as Outcome.Success).value

        assertTrue(PhotoCompressor.isJpeg(result), "결과 머리 FF D8 FF")
        assertEquals(1_200 to 1_600, boundsOf(result))
    }

    @Test
    fun `AN5-C-2 긴 변 64px 미만 PNG도 JPEG로 바꾼다`() {
        val png = pngOf(Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) })

        val result = (compressor.compress(png) as Outcome.Success).value

        assertTrue(PhotoCompressor.isJpeg(result))
        assertEquals(48 to 48, boundsOf(result), "작은 그림은 키우지 않는다")
    }

    @Test
    fun `투명한 PNG는 흰 바탕에 그려 JPEG로 바꾼다`() {
        val png = pngOf(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) })

        val result = (compressor.compress(png) as Outcome.Success).value

        val pixel = BitmapFactory.decodeByteArray(result, 0, result.size).getPixel(50, 50)
        assertTrue(
            Color.red(pixel) > WHITE_MIN && Color.green(pixel) > WHITE_MIN && Color.blue(pixel) > WHITE_MIN,
            "흰 바탕"
        )
    }

    private fun pngOf(bitmap: Bitmap): ByteArray = java.io.ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    private fun jpegWithExif(width: Int, height: Int, orientation: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val file = File.createTempFile("exif", ".jpg", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,30/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_MODEL, "Runkk Phone")
            saveAttributes()
        }
        val bytes = file.readBytes()
        assertTrue(containsExifSegment(bytes), "시험 원본에는 EXIF가 있다")
        return bytes
    }

    private fun boundsOf(jpeg: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
        return options.outWidth to options.outHeight
    }

    /** JPEG 안에 APP1(FF E1) 뒤 "Exif" 식별자가 있는가. */
    private fun containsExifSegment(jpeg: ByteArray): Boolean = (0 until jpeg.size - 6).any { i ->
        jpeg[i] == 0xFF.toByte() && jpeg[i + 1] == 0xE1.toByte() &&
            jpeg[i + 4] == 'E'.code.toByte() && jpeg[i + 5] == 'x'.code.toByte()
    }

    private companion object {
        /** JPEG 손실을 감안한 흰색 하한. */
        const val WHITE_MIN = 240
    }
}
