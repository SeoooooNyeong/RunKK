package com.a207.runkk.app.api.crew

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PhotoCompressor 순서·거부(TC-6511·TC-6513·TC-6514·TC-6517·TC-6519·TC-6520, D-142): 가짜 인코더가 시도를 기록한다. */
class PhotoCompressorTest {
    private val photos = FakePhotos()
    private val compressor = photos.compressor

    @Test
    fun `TC-6519 품질 85에서 5씩 하한 50, 그래도 넘으면 긴 변 0·8배로 다시 85부터 시도한다`() {
        photos.encodedSize = { edge, quality -> if (edge == 1_280 && quality <= 75) 900_000 else 2_000_000 }

        val result = compressor.compress(FakePhotos.jpeg(8_000_000))

        assertEquals(
            listOf(
                1_600 to 85,
                1_600 to 80,
                1_600 to 75,
                1_600 to 70,
                1_600 to 65,
                1_600 to 60,
                1_600 to 55,
                1_600 to 50,
                1_280 to 85,
                1_280 to 80,
                1_280 to 75
            ),
            photos.attempts
        )
        assertEquals(900_000, (result as Outcome.Success).value.size)
    }

    @Test
    fun `TC-6511·TC-6517 결과는 1MB(1,048,576B) 이하가 되는 첫 품질이다`() {
        photos.encodedSize = { _, quality -> if (quality <= 70) 1_048_576 else 1_048_577 }

        val result = compressor.compress(FakePhotos.jpeg(10 * 1024 * 1024))

        assertEquals(1_048_576, (result as Outcome.Success).value.size, "정확히 10MB 원본은 받는다")
        assertEquals(1_600 to 70, photos.attempts.last())
    }

    @Test
    fun `TC-6513 긴 변이 1600보다 작으면 키우지 않고, 크면 1600으로 맞춘다`() {
        for ((longEdge, expected) in listOf(1_599 to 1_599, 1_600 to 1_600, 1_601 to 1_600)) {
            photos.attempts.clear()
            photos.size = longEdge to 1_000

            compressor.compress(FakePhotos.jpeg(2_000_000))

            assertEquals(expected, photos.attempts.first().first, "긴 변 $longEdge")
        }
    }

    @Test
    fun `AN5-C-2 긴 변이 64px보다 작은 그림도 한 번은 인코딩해 성공한다`() {
        photos.size = 48 to 32

        val result = compressor.compress(FakePhotos.jpeg(2_000))

        assertEquals(500_000, (result as Outcome.Success).value.size)
        assertEquals(listOf(48 to 85), photos.attempts, "키우지 않고 품질 85 한 번")
    }

    @Test
    fun `AN5-C-2 작은 그림이 1MB를 못 넘기면 그 크기에서 품질만 낮춰 본 뒤 TOO_LARGE다`() {
        photos.size = 48 to 48
        photos.encodedSize = { _, _ -> 2_000_000 }

        val result = compressor.compress(FakePhotos.jpeg(2_000))

        assertEquals(Outcome.Failure(AppError.Rejected("TOO_LARGE")), result)
        assertEquals((85 downTo 50 step 5).map { 48 to it }, photos.attempts)
    }

    @Test
    fun `TC-6514 10MB 초과·0바이트·GIF·풀 수 없는 원본은 줄이지 않고 거부 값을 돌려준다`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0)
        assertEquals(Outcome.Failure(AppError.Rejected("TOO_LARGE")), compressor.compress(FakePhotos.jpeg(10_485_761)))
        assertEquals(Outcome.Failure(AppError.Rejected("UNSUPPORTED_IMAGE")), compressor.compress(ByteArray(0)))
        assertEquals(
            Outcome.Failure(AppError.Rejected("UNSUPPORTED_IMAGE")),
            compressor.compress("GIF89a".toByteArray())
        )
        photos.isDecodable = false
        assertEquals(Outcome.Failure(AppError.Rejected("UNSUPPORTED_IMAGE")), compressor.compress(FakePhotos.jpeg(100)))
        assertEquals(Outcome.Failure(AppError.Rejected("UNSUPPORTED_IMAGE")), compressor.compress(png))
        assertEquals(emptyList(), photos.attempts, "인코딩 시도 0")
    }

    @Test
    fun `읽을 수 없는 사진 주소는 거부 값이다`() {
        assertEquals(Outcome.Failure(AppError.Rejected("UNSUPPORTED_IMAGE")), compressor.compress("content://none"))
    }

    @Test
    fun `TC-6520 PhotoCompressor 소스는 원본 메타데이터를 새 파일로 복사하지 않는다`() {
        val source =
            File("src/main/kotlin/com/a207/runkk/app/api/crew/PhotoCompressor.kt").takeIf(File::exists)
                ?: File("data/src/main/kotlin/com/a207/runkk/app/api/crew/PhotoCompressor.kt")
        val code = source.readText()
        assertTrue(code.isNotEmpty())
        assertTrue("setAttribute(" !in code && "saveAttributes(" !in code, "ExifInterface 복사 금지(D-142 (1))")
    }
}
