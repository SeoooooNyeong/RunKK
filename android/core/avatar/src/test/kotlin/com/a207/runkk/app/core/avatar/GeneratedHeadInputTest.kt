package com.a207.runkk.app.core.avatar

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 셀카 생성 결과 JSON → 입력 값(8 PNG, 1024×1024 공통 좌표, nullable mobile 메타)과 입력 정책(PoC SelfiePolicy·GeneratedAvatarLayers.decode).
 */
class GeneratedHeadInputTest {
    @Test
    fun `여덟 PNG Data URL을 바이트로 풀고 mobile 메타를 읽는다`() {
        val input = GeneratedHeadInput.parse(
            json(mobile = """{"neckMask":{"centerX":512.5,"chinY":760},"skinColor":[240,200,180]}""")
        )

        assertContentEquals(png(1), input.face)
        assertContentEquals(png(2), input.eyes)
        assertContentEquals(png(3), input.hair)
        assertContentEquals(png(4), input.rightFace)
        assertContentEquals(png(5), input.rightEyes)
        assertContentEquals(png(6), input.rightHair)
        assertContentEquals(png(7), input.frontImage)
        assertContentEquals(png(8), input.rightImage)
        assertEquals(GeneratedHeadInput.ReportedNeck(512.5f, 760f), input.neckMask)
        assertEquals(argb(240, 200, 180), input.skinColor)
    }

    @Test
    fun `mobile·neckMask·skinColor가 없거나 null이면 메타 없이 받는다`() {
        for (mobile in listOf(null, "null", "{}", """{"neckMask":null,"skinColor":null}""")) {
            val input = GeneratedHeadInput.parse(json(mobile = mobile))
            assertNull(input.neckMask, "mobile=$mobile")
            assertNull(input.skinColor, "mobile=$mobile")
        }
    }

    @Test
    fun `목 값이 하나만 있거나 숫자가 아니면 목 메타가 없는 것으로 본다`() {
        val partial = GeneratedHeadInput.parse(json(mobile = """{"neckMask":{"centerX":512}}"""))
        assertNull(partial.neckMask)
        val text = GeneratedHeadInput.parse(json(mobile = """{"neckMask":{"centerX":"a","chinY":700}}"""))
        assertNull(text.neckMask)
    }

    @Test
    fun `피부색은 세 값일 때만 쓰고 0~255로 자른다`() {
        assertNull(GeneratedHeadInput.parse(json(mobile = """{"skinColor":[1,2]}""")).skinColor)
        assertNull(GeneratedHeadInput.parse(json(mobile = """{"skinColor":["a",2,3]}""")).skinColor)
        assertEquals(argb(255, 0, 7), GeneratedHeadInput.parse(json(mobile = """{"skinColor":[300,-4,7]}""")).skinColor)
    }

    @Test
    fun `완성 정면·측면 원본은 합성에 쓰지 않으므로 없어도 받는다`() {
        val input = GeneratedHeadInput.parse(json(omit = setOf("frontImage", "rightImage")))
        assertNull(input.frontImage)
        assertNull(input.rightImage)
        assertNotNull(input.rightHair)
    }

    @Test
    fun `합성 레이어 여섯 장 가운데 하나라도 없으면 실패한다`() {
        for (field in LAYER_FIELDS) {
            assertFailsWith<IllegalArgumentException>(field) { GeneratedHeadInput.parse(json(omit = setOf(field))) }
        }
    }

    @Test
    fun `PNG Data URL이 아니거나 1024×1024가 아니면 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            GeneratedHeadInput.parse(json(override = mapOf("eyesImage" to "data:image/jpeg;base64,AAAA")))
        }
        assertFailsWith<IllegalArgumentException> {
            GeneratedHeadInput.parse(json(override = mapOf("hairImage" to "data:image/png;base64,@@@")))
        }
        assertFailsWith<IllegalArgumentException> {
            GeneratedHeadInput.parse(json(override = mapOf("faceImage" to dataUrl(png(1, size = 512)))))
        }
        assertFailsWith<IllegalArgumentException> {
            GeneratedHeadInput.parse(json(override = mapOf("faceImage" to "data:image/png;base64," + b64("not png"))))
        }
    }

    @Test
    fun `결과와 Data URL 크기 상한을 넘으면 읽지 않는다`() {
        val huge = "data:image/png;base64," + "A".repeat(HeadResultPolicy.MAX_DATA_URL_CHARS)
        assertFailsWith<IllegalArgumentException> { HeadResultPolicy.decodePng("faceImage", huge) }
        assertFailsWith<IllegalArgumentException> {
            HeadResultPolicy.checkResultLength(HeadResultPolicy.MAX_RESULT_CHARS + 1)
        }
        HeadResultPolicy.checkResultLength(1024)
    }

    @Test
    fun `JSON 객체가 아니면 실패한다`() {
        assertFailsWith<IllegalArgumentException> { GeneratedHeadInput.parse("[]") }
        assertFailsWith<IllegalArgumentException> { GeneratedHeadInput.parse("{") }
    }

    private fun json(
        mobile: String? = null,
        omit: Set<String> = emptySet(),
        override: Map<String, String> = emptyMap()
    ): String {
        val fields = ALL_FIELDS.withIndex()
            .filter { it.value !in omit }
            .map { (index, name) -> "\"$name\":\"${override[name] ?: dataUrl(png(index + 1))}\"" }
        val meta = mobile?.let { listOf("\"mobile\":$it") }.orEmpty()
        return (fields + meta + "\"headId\":\"h1\"").joinToString(",", "{", "}")
    }

    private companion object {
        val LAYER_FIELDS =
            listOf("faceImage", "eyesImage", "hairImage", "rightFaceImage", "rightEyesImage", "rightHairImage")
        val ALL_FIELDS = LAYER_FIELDS + listOf("frontImage", "rightImage")
        private val cache = mutableMapOf<Pair<Int, Int>, ByteArray>()

        /** 1024×1024(기본) 투명 PNG. 서로 구분되도록 픽셀 하나에 [marker]를 칠한다. */
        fun png(marker: Int, size: Int = 1024): ByteArray = cache.getOrPut(marker to size) {
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, (0xff shl 24) or marker)
            ByteArrayOutputStream().use { out ->
                check(ImageIO.write(image, "png", out))
                out.toByteArray()
            }
        }

        fun dataUrl(bytes: ByteArray) = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)

        fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

        fun argb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }
}
