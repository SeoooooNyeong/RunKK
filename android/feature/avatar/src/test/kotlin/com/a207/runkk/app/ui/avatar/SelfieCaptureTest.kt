package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 셀카 원본 읽기·지우기(리뷰 RC-1·RC-2·RC-4, FR-156): 사진을 읽다 무슨 예외가 나도 앱을 멈추지 않고 「사진을 받지 못했어요」(null)로
 * 끝내며, 카메라 원본(EXIF·위치 정보)은 성공·실패·취소 어느 쪽이든 캐시에 남지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class SelfieCaptureTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `깨진 JPEG은 null이다`() {
        assertNull(SelfieImage.decode(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)))
    }

    @Test
    fun `읽기 중 IllegalArgumentException·런타임 예외·OOM이 나도 null이다`() {
        assertNull(SelfieImage.decode { throw IllegalArgumentException("bad uri") })
        assertNull(SelfieImage.decode { throw IllegalStateException("exif") })
        assertNull(SelfieImage.decode { throwingStream(OutOfMemoryError("big")) })
    }

    @Test
    fun `취소는 삼키지 않고 다시 던진다`() {
        assertFailsWith<CancellationException> { SelfieImage.decode { throw CancellationException("left") } }
    }

    @Test
    fun `촬영 직전 준비는 남은 원본을 지우고 폴더를 만든다`() {
        val leftover = SelfieCapture.fileOf(context)
        leftover.parentFile?.deleteRecursively()
        val file = SelfieCapture.prepare(context)
        assertTrue(file.parentFile?.isDirectory == true, "촬영 직전에 폴더가 있다")
        file.writeBytes(byteArrayOf(1))
        val again = SelfieCapture.prepare(context)
        assertFalse(again.exists(), "앞 촬영의 원본이 남아 있으면 지운다")
    }

    @Test
    fun `앨범을 고를 때도 남은 카메라 원본을 지운다`() {
        val file = SelfieCapture.prepare(context).apply { writeBytes(byteArrayOf(1)) }
        SelfieCapture.clear(context)
        assertFalse(file.exists())
    }

    @Test
    fun `원본은 읽기가 성공·실패·예외 어느 쪽이든 지운다`() {
        val file = SelfieCapture.prepare(context)
        file.writeBytes(byteArrayOf(1))
        SelfieCapture.readThenDelete(file) { null as Bitmap? }
        assertFalse(file.exists(), "읽지 못해도 지운다")

        file.writeBytes(byteArrayOf(1))
        assertFailsWith<CancellationException> {
            SelfieCapture.readThenDelete(file) { throw CancellationException("left") }
        }
        assertFalse(file.exists(), "화면을 떠나 취소돼도 지운다")
    }

    private fun throwingStream(error: Throwable): InputStream = object : ByteArrayInputStream(ByteArray(0)) {
        override fun read(b: ByteArray, off: Int, len: Int): Int = throw error
        override fun read(): Int = throw error
    }
}
