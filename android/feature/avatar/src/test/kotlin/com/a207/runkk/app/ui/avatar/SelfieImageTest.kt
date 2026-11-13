package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 셀카 방향(실기기 DV3-3): 세로로 찍은 사진의 EXIF 방향을 픽셀에 적용해 미리보기·보내는 JPEG·보낸 사진 사본이 모두 바로 선다.
 * 표본은 가로 40 × 세로 20, 왼쪽 위 칸만 빨강이다.
 */
@RunWith(RobolectricTestRunner::class)
class SelfieImageTest {
    private fun sample(): Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
        for (x in 0 until MARK) for (y in 0 until MARK) setPixel(x, y, Color.RED)
    }

    private fun Bitmap.isRedAt(x: Int, y: Int): Boolean =
        Color.red(getPixel(x, y)) > 200 && Color.green(getPixel(x, y)) < 80

    @Test
    fun `EXIF 6(시계 방향 90도)이면 세로로 세우고 빨간 칸이 오른쪽 위로 간다`() {
        val upright = SelfieImage.oriented(sample(), ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(HEIGHT, upright.width)
        assertEquals(WIDTH, upright.height)
        assertTrue(upright.isRedAt(upright.width - 1, 0))
    }

    @Test
    fun `EXIF 8(반시계 방향 90도)이면 세로로 세우고 빨간 칸이 왼쪽 아래로 간다`() {
        val upright = SelfieImage.oriented(sample(), ExifInterface.ORIENTATION_ROTATE_270)
        assertEquals(HEIGHT, upright.width)
        assertEquals(WIDTH, upright.height)
        assertTrue(upright.isRedAt(0, upright.height - 1))
    }

    @Test
    fun `EXIF 3(180도)이면 크기는 같고 빨간 칸이 오른쪽 아래로 간다`() {
        val upright = SelfieImage.oriented(sample(), ExifInterface.ORIENTATION_ROTATE_180)
        assertEquals(WIDTH, upright.width)
        assertEquals(HEIGHT, upright.height)
        assertTrue(upright.isRedAt(upright.width - 1, upright.height - 1))
    }

    @Test
    fun `방향이 없거나 정상이면 그대로 둔다`() {
        val source = sample()
        assertTrue(SelfieImage.oriented(source, ExifInterface.ORIENTATION_NORMAL) === source)
        assertTrue(SelfieImage.oriented(source, ExifInterface.ORIENTATION_UNDEFINED) === source)
    }

    @Test
    fun `EXIF 6이 붙은 JPEG을 읽으면 바로 선 그림이고, 보내는 JPEG도 바로 선 그림이다`() {
        val jpeg = ByteArrayOutputStream().use { out ->
            sample().compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.toByteArray()
        }
        val tagged = withOrientation(jpeg, ExifInterface.ORIENTATION_ROTATE_90)

        val upright = SelfieImage.decode(tagged) ?: error("디코딩 실패")
        assertEquals(HEIGHT, upright.width)
        assertEquals(WIDTH, upright.height)

        val sent = BitmapFactory.decodeByteArray(SelfieEncoder.encode(upright), 0, SelfieEncoder.encode(upright).size)
        assertTrue(sent.height > sent.width, "보내는 JPEG은 세로로 선다(방향 태그 없이 픽셀이 바로 섬)")
    }

    /** JPEG에 EXIF 방향을 붙인다(임시 파일로 써서 ExifInterface로 고친다). */
    private fun withOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val file = kotlin.io.path.createTempFile(suffix = ".jpg").toFile()
        file.writeBytes(jpeg)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        val tagged = file.readBytes()
        file.delete()
        assertEquals(orientation, ExifInterface(ByteArrayInputStream(tagged)).rotationOrientation())
        return tagged
    }

    private fun ExifInterface.rotationOrientation(): Int =
        getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

    private companion object {
        const val WIDTH = 40
        const val HEIGHT = 20
        const val MARK = 4
    }
}
