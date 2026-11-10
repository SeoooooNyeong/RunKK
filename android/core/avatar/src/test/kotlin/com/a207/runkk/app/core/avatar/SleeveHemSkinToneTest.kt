package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * 실기기 결함 DV-성-11: 러닝량이 높아 피부가 어두우면 소매 끝 바로 아래 팔에 톤을 받지 않은 밝은 원래 피부색 띠가 남았다. 승인 원본
 * (남자 선택 몸은 avatar_male_idle.riv 내장 1122×1402, 서기 몸은 avatar_legacy_standing_body.png 512×768)에서 소매 끝 윤곽선과
 * 피부 판정 영역 위쪽 경계 사이의 팔 피부 표본이 톤을 받는지, 소매 안 분홍 그늘(피부색 판정을 통과하는 옷)은 그대로인지 본다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleeveHemSkinToneTest {
    private val raw = File("src/main/res/raw")

    @Test
    fun `남자 선택 몸 기본 상의 - 러닝량 100이면 소매 끝 바로 아래 팔 피부도 어두워지고 소매 안 그늘은 그대로다`() {
        val source = embeddedPng(File(raw, "avatar_male_idle.riv").readBytes(), MaleSelectedBody.ASSET)
        val prepared = BodySkinLayers.prepare(MaleSelectedBody.ASSET, source)
        val original = decode(source)
        val rendered = decode(BodySkinLayers.render(prepared, amount = 100, maleOutfit = true))

        // 왼팔·오른팔 소매 끝 윤곽선 바로 아래(전에는 다각형 위쪽 경계 y831·832 밖이었다).
        listOf(427 to 827, 432 to 829, 439 to 833, 444 to 836, 701 to 826, 696 to 830, 692 to 835, 681 to 837)
            .forEach { (x, y) -> assertToned(original.getPixel(x, y), rendered.getPixel(x, y), "팔 ($x, $y)") }
        // 소매 끝 윤곽선 위 소매 안 분홍 그늘. 피부색 판정은 통과하지만 옷이라 톤을 받지 않는다.
        listOf(475 to 828, 465 to 837, 655 to 829)
            .forEach { (x, y) -> assertUnchanged(original.getPixel(x, y), rendered.getPixel(x, y), "소매 ($x, $y)") }
        original.recycle()
        rendered.recycle()
        assertSame(source, BodySkinLayers.render(prepared, amount = 0, maleOutfit = true), "러닝량 0은 원본 그대로")
    }

    @Test
    fun `서기 몸 기본 상의 - 러닝량 100이면 소매 끝 바로 아래 팔 피부도 어두워지고 소매 안 그늘은 그대로다`() {
        val source = File(raw, "avatar_legacy_standing_body.png").readBytes()
        val legacy = BodySkinLayers.prepare(BodySkinLayers.STANDING_BODY_LEGACY, source)
        val original = decode(source)
        for (male in listOf(false, true)) {
            val rendered = decode(BodySkinLayers.renderStanding(legacy, legacy, 100, AvatarOutfit(), maleOutfit = male))
            listOf(88 to 167, 90 to 169, 93 to 170, 99 to 171, 423 to 164, 420 to 167, 414 to 169, 406 to 173)
                .forEach { (x, y) ->
                    assertToned(original.getPixel(x, y), rendered.getPixel(x, y), "팔 ($x, $y) 남자=$male")
                }
            listOf(88 to 158, 92 to 160, 136 to 161, 417 to 159, 375 to 160)
                .forEach { (x, y) ->
                    assertUnchanged(original.getPixel(x, y), rendered.getPixel(x, y), "소매 ($x, $y) 남자=$male")
                }
            rendered.recycle()
        }
        original.recycle()
        assertSame(source, BodySkinLayers.render(legacy, amount = 0), "러닝량 0·기본 의상은 원본 그대로")
    }

    @Test
    fun `남자 카라티(상의 2)를 입혀도 소매 끝 아래 팔에 톤 전 밝은 피부색이 남지 않는다`() {
        val source = File(raw, "avatar_legacy_standing_body.png").readBytes()
        val legacy = BodySkinLayers.prepare(BodySkinLayers.STANDING_BODY_LEGACY, source)
        val original = decode(source)
        val rendered =
            decode(BodySkinLayers.renderStanding(legacy, legacy, 100, AvatarOutfit(top = 2), maleOutfit = true))
        // 카라티 소매 끝이 덮지 않는 오른팔 바깥 모서리.
        listOf(426 to 167, 424 to 168, 422 to 169)
            .forEach { (x, y) -> assertToned(original.getPixel(x, y), rendered.getPixel(x, y), "팔 ($x, $y)") }
        original.recycle()
        rendered.recycle()
    }

    /** 옷 픽셀은 톤을 받지 않는다. PNG 왕복의 프리멀티플라이드 반올림만 채널마다 2까지 허용한다. */
    private fun assertUnchanged(source: Int, actual: Int, message: String) = assertNear(source, actual, 2, message)

    /** 러닝량 100 톤(R×0.70, G×0.50, B×0.32)을 받았는지. 알파 253 프리멀티플라이드 반올림을 감안해 채널마다 3까지 허용한다. */
    private fun assertToned(source: Int, actual: Int, message: String) {
        val expected = Color.rgb(
            (Color.red(source) * .70f).toInt(),
            (Color.green(source) * .50f).toInt(),
            (Color.blue(source) * .32f).toInt()
        )
        assertNear(expected, actual, 3, "$message(원본 ${Integer.toHexString(source)})")
    }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int, message: String) {
        val channels = listOf(Color::red, Color::green, Color::blue)
        assertTrue(
            channels.all { kotlin.math.abs(it(expected) - it(actual)) <= tolerance },
            "$message: 기대 ${Integer.toHexString(expected)}, 실제 ${Integer.toHexString(actual)}"
        )
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val PNG_END = "IEND".toByteArray()

        /** Rive 파일에서 이름이 [name]인 이미지 자산의 내장 PNG(이름 뒤 첫 PNG, IEND 청크와 CRC까지). */
        fun embeddedPng(riv: ByteArray, name: String): ByteArray {
            val start = indexOf(riv, PNG_SIGNATURE, indexOf(riv, name.toByteArray(), 0))
            return riv.copyOfRange(start, indexOf(riv, PNG_END, start) + PNG_END.size + 4)
        }

        fun indexOf(data: ByteArray, key: ByteArray, from: Int): Int {
            val at = (from..data.size - key.size).firstOrNull { i -> key.indices.all { data[i + it] == key[it] } }
            return checkNotNull(at) { "${String(key, Charsets.ISO_8859_1)} not found" }
        }

        fun decode(bytes: ByteArray): Bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }
}
