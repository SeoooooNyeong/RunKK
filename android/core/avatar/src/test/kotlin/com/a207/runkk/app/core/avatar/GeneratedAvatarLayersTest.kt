package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * 셀카 생성 결과 → 남녀 몸체용 머리 레이어(PoC GeneratedAvatarLayers.prepare). 합성 자료는 도형 픽스처다:
 * 정면 얼굴(머리 x312..712·y300..750, 목 x467..557·y750..824), 눈 두 점, 헤어 띠(정수리 y150), 측면도 같은 꼴.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneratedAvatarLayersTest {
    @Test
    fun `목 메타 없이도 얼굴·눈·헤어에 같은 배치 변환을 적용해 남녀 몸체 목표에 맞춘다`() {
        val layers = GeneratedHeadLayers.fromHeadJson(json(mobile = null))

        for (gender in AvatarGender.entries) {
            val target = HeadTarget.front(gender)
            val placement = HeadPlacement.fit(512f, 750f, 150f, target)
            val front = checkNotNull(layers.variants.getValue(gender).front)
            assertEquals(setOf("face_layer", "hair_layer", "eyes_open", "eyes_half", "eyes_closed"), front.keys)
            front.values.forEach { assertEquals(1024 to 1024, HeadResultPolicy.pngSize(it)) }

            val hair = decode(front.getValue("hair_layer"))
            assertNear(target.crownY, alphaTop(hair).toFloat(), 2f, "$gender 헤어 정수리")
            val eyes = decode(front.getValue("eyes_open"))
            val (eyeX, eyeY) = alphaCentroid(eyes, 0 until 1024)
            assertNear(placement.mapX(512f), eyeX, 3f, "$gender 두 눈 중심 x")
            assertNear(placement.mapY(510f), eyeY, 3f, "$gender 눈 y")
            val face = decode(front.getValue("face_layer"))
            val chinX = placement.mapX(512f).toInt()
            assertTrue(Color.alpha(face.getPixel(chinX, (placement.mapY(740f)).toInt())) > 200, "$gender 턱 위는 얼굴")
            assertTrue(
                Color.alpha(face.getPixel(placement.mapX(330f).toInt(), placement.mapY(800f).toInt())) < 10,
                "목 옆은 비었다"
            )
            listOf(hair, eyes, face).forEach(Bitmap::recycle)
        }
    }

    @Test
    fun `목 메타가 있으면 그 값을 쓰고 피부색 메타가 없으면 턱 위 피부 중앙값을 쓴다`() {
        val reported = GeneratedHeadLayers.fromHeadJson(json(mobile = """{"neckMask":{"centerX":512,"chinY":750}}"""))
        assertEquals(FRONT_SKIN, reported.skinColor)
        val detected = GeneratedHeadLayers.fromHeadJson(json(mobile = """{"neckMask":null,"skinColor":null}"""))
        assertEquals(FRONT_SKIN, detected.skinColor)
        val colored = GeneratedHeadLayers.fromHeadJson(json(mobile = """{"skinColor":[10,20,30]}"""))
        assertEquals(Color.rgb(10, 20, 30), colored.skinColor)
    }

    @Test
    fun `측면 레이어도 몸체 측면 목표에 맞추고 볼 레이어 2단계를 만든다`() {
        val layers = GeneratedHeadLayers.fromHeadJson(json(mobile = null))
        for (gender in AvatarGender.entries) {
            val variant = layers.variants.getValue(gender)
            assertEquals(setOf(1, 2), variant.frontFullness.keys)
            assertEquals(setOf(1, 2), variant.sideFullness.keys)
            assertEquals(null, variant.sideLayers.neckRegistration, "새 셀카에는 내장 샘플 목 등록을 쓰지 않는다")
            val sideHair = decode(variant.sideLayers.hair)
            assertNear(HeadTarget.side(gender).crownY, alphaTop(sideHair).toFloat(), 2f, "$gender 측면 정수리")
            sideHair.recycle()
        }
    }

    @Test
    fun `체형 볼 레이어는 눈을 덮지 않는다 - 눈이 턱 가까이 있어도 눈 아래부터 넓힌다(D-824)`() {
        // 큰 눈이 턱 쪽으로 내려온 셀카(눈 y600..660, 턱 750): 턱 위 145px 띠가 눈 아래쪽을 덮으면 눈이 감긴 선처럼 보였다.
        val lowEyes = shape { bitmap ->
            fill(bitmap, 400..440, 600..660, EYE)
            fill(bitmap, 584..624, 600..660, EYE)
        }
        val layers = GeneratedHeadLayers.fromHeadJson(json(mobile = null, eyes = lowEyes))
        for (gender in AvatarGender.entries) {
            val placement = HeadPlacement.fit(512f, 750f, 150f, HeadTarget.front(gender))
            val eyeTop = placement.mapY(600f).toInt()
            val eyeBottom = placement.mapY(660f).toInt()
            val eyeXs = listOf(placement.mapX(420f).toInt(), placement.mapX(604f).toInt())
            for (stage in 1..2) {
                val overlay = decode(layers.variants.getValue(gender).frontFullness.getValue(stage))
                for (x in eyeXs) {
                    for (y in eyeTop until eyeBottom) {
                        assertTrue(
                            Color.alpha(overlay.getPixel(x, y)) == 0,
                            "$gender 체형 $stage 볼 레이어가 눈($x, $y)을 덮는다"
                        )
                    }
                }
                val chin = HeadTarget.front(gender).chinY.toInt()
                assertTrue(Color.alpha(overlay.getPixel(placement.mapX(512f).toInt(), chin - 10)) > 200, "턱 위 볼은 그대로")
                overlay.recycle()
            }
        }
    }

    @Test
    fun `층별로 받은 PNG와 메타로 만든 머리는 통짜 응답 JSON으로 만든 머리와 같다(S15P31A207-300 레이어 합성)`() {
        val mobile = """{"neckMask":{"centerX":512,"chinY":750},"skinColor":[10,20,30]}"""
        val whole = GeneratedHeadLayers.fromHeadJson(json(mobile = mobile))
        val layered = GeneratedHeadLayers.fromLayers(
            face = frontFace,
            eyes = frontEyes,
            hair = frontHair,
            rightFace = sideFace,
            rightEyes = sideEyes,
            rightHair = sideHair,
            metaJson = """{"format":"png1","headId":"h","canvas":1024,"mobile":$mobile}"""
        )

        assertEquals(whole.skinColor, layered.skinColor)
        for (gender in AvatarGender.entries) {
            val expected = whole.variants.getValue(gender)
            val actual = layered.variants.getValue(gender)
            assertEquals(expected.front.keys, actual.front.keys)
            expected.front.forEach { (slot, png) ->
                assertSamePixels(png, actual.front.getValue(slot), "$gender $slot")
            }
            assertSamePixels(expected.sideLayers.face, actual.sideLayers.face, "$gender 측면 얼굴")
            assertSamePixels(expected.sideLayers.eyes, actual.sideLayers.eyes, "$gender 측면 눈")
            assertSamePixels(expected.sideLayers.hair, actual.sideLayers.hair, "$gender 측면 헤어")
            expected.frontFullness.forEach { (step, png) ->
                assertSamePixels(png, actual.frontFullness.getValue(step), "$gender 볼 $step")
            }
        }
        // 메타에 합성 정보가 없으면 얼굴 알파에서 목을 찾는다(통짜 응답과 같다)
        assertEquals(
            GeneratedHeadLayers.fromHeadJson(json(mobile = null)).skinColor,
            GeneratedHeadLayers.fromLayers(
                frontFace,
                frontEyes,
                frontHair,
                sideFace,
                sideEyes,
                sideHair,
                "{}"
            ).skinColor
        )
    }

    @Test
    fun `층별 입력도 1024 PNG가 아니면 실패한다`() {
        val small = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).let { bitmap ->
            ByteArrayOutputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                bitmap.recycle()
                it.toByteArray()
            }
        }
        assertFailsWith<IllegalArgumentException> {
            GeneratedHeadLayers.fromLayers(small, frontEyes, frontHair, sideFace, sideEyes, sideHair, "{}")
        }
        assertFailsWith<IllegalArgumentException> {
            GeneratedHeadLayers.fromLayers(frontFace, frontEyes, frontHair, sideFace, sideEyes, sideHair, "not json")
        }
    }

    private fun assertSamePixels(expected: ByteArray, actual: ByteArray, message: String) {
        val a = decode(expected)
        val b = decode(actual)
        try {
            assertEquals(a.width to a.height, b.width to b.height, message)
            assertTrue(pixels(a).contentEquals(pixels(b)), "$message 픽셀이 같다")
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    @Test
    fun `목 메타가 없고 알파에서도 목을 찾지 못하면 실패한다`() {
        val noNeck = shape { fill(it, 300..720, 300..800, FRONT_SKIN) }
        assertFailsWith<IllegalArgumentException> {
            GeneratedHeadLayers.fromHeadJson(json(mobile = null, face = noNeck))
        }
    }

    private fun json(mobile: String?, face: ByteArray = frontFace, eyes: ByteArray = frontEyes): String {
        val images = mapOf(
            "faceImage" to face,
            "eyesImage" to eyes,
            "hairImage" to frontHair,
            "rightFaceImage" to sideFace,
            "rightEyesImage" to sideEyes,
            "rightHairImage" to sideHair
        )
        val fields = images.map { (name, bytes) -> "\"$name\":\"data:image/png;base64,${b64(bytes)}\"" }
        return (fields + listOfNotNull(mobile?.let { "\"mobile\":$it" })).joinToString(",", "{", "}")
    }

    private companion object {
        val FRONT_SKIN = Color.rgb(241, 200, 176)
        val EYE = Color.rgb(40, 30, 30)
        val HAIR = Color.rgb(60, 40, 30)

        val frontFace by lazy {
            shape { bitmap ->
                fill(bitmap, 312..712, 300 until 750, FRONT_SKIN)
                fill(bitmap, 467..557, 750 until 824, FRONT_SKIN)
            }
        }
        val frontEyes by lazy {
            shape { bitmap ->
                fill(bitmap, 400..440, 500..520, EYE)
                fill(bitmap, 584..624, 500..520, EYE)
            }
        }
        val frontHair by lazy { shape { fill(it, 300..724, 150..330, HAIR) } }
        val sideFace by lazy {
            shape { bitmap ->
                fill(bitmap, 450..850, 300 until 760, FRONT_SKIN)
                fill(bitmap, 600..679, 760 until 840, FRONT_SKIN)
            }
        }
        val sideEyes by lazy { shape { fill(it, 760..790, 500..520, EYE) } }
        val sideHair by lazy { shape { fill(it, 440..860, 160..330, HAIR) } }

        fun shape(draw: (Bitmap) -> Unit): ByteArray {
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            return try {
                draw(bitmap)
                ByteArrayOutputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    it.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }

        fun fill(bitmap: Bitmap, xs: IntRange, ys: IntRange, color: Int) {
            val width = xs.last - xs.first + 1
            val row = IntArray(width) { color }
            for (y in ys) bitmap.setPixels(row, 0, width, xs.first, y, width, 1)
        }

        fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun decode(bytes: ByteArray): Bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

        fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

        fun alphaTop(bitmap: Bitmap): Int {
            val pixels = pixels(bitmap)
            return pixels.indices.first { Color.alpha(pixels[it]) >= 128 } / bitmap.width
        }

        fun alphaCentroid(bitmap: Bitmap, xs: IntRange): Pair<Float, Float> {
            val pixels = pixels(bitmap)
            var sumX = 0.0
            var sumY = 0.0
            var count = 0
            for (index in pixels.indices) {
                val x = index % bitmap.width
                if (x in xs && Color.alpha(pixels[index]) >= 128) {
                    sumX += x
                    sumY += index / bitmap.width
                    count++
                }
            }
            check(count > 0)
            return (sumX / count).toFloat() to (sumY / count).toFloat()
        }

        fun assertNear(expected: Float, actual: Float, tolerance: Float, message: String) =
            assertTrue(abs(expected - actual) <= tolerance, "$message: 기대 $expected, 실제 $actual")
    }
}
