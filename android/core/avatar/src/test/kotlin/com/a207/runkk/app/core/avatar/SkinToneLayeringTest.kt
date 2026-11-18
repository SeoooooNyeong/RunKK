package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * 디자인 규칙(아바타 PoC 온보딩 3절): 피부톤 필터는 피부에만 쓰고 눈·헤어·안경·옷에는 쓰지 않는다. 피부에 톤을 적용한 뒤 옷을 얹는다.
 * 러닝량 0은 원본 바이트 그대로라 바꿀 때마다 누적되지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkinToneLayeringTest {
    @Test
    fun `새 기본 여자 헤어는 옛 목 앞 가닥 영역도 원본대로 그린다`() {
        val layers = SideHeadLayers(
            face = png(1024, 1024) {},
            eyes = png(1024, 1024) {},
            hair = png(1024, 1024) { fill(it, 700..730, 800..830, HAIR) },
            neckRegistration = SideNeckRegistration.FEMALE
        )
        val composed = decode(AvatarImages.composeSideHead(layers, amount = 0))
        try {
            assertEquals(HAIR, composed.getPixel(715, 815))
        } finally {
            composed.recycle()
        }
    }

    @Test
    fun `측면 머리는 얼굴 피부만 어둡게 하고 눈·헤어 색은 그대로 얹는다`() {
        val layers = SideHeadLayers(
            face = png(64, 64) { fill(it, 0..63, 0..63, SKIN) },
            eyes = png(64, 64) { fill(it, 10..12, 10..12, EYE) },
            hair = png(64, 64) { fill(it, 40..50, 0..5, HAIR) }
        )
        val composed = decode(AvatarImages.composeSideHead(layers, amount = 100))

        assertEquals(EYE, composed.getPixel(11, 11), "눈")
        assertEquals(HAIR, composed.getPixel(45, 2), "헤어")
        assertNear(Color.rgb(214, 144, 116), composed.getPixel(30, 30), "피부(R×0.88, G×0.80, B×0.728)")
        composed.recycle()
    }

    @Test
    fun `러닝량 0은 원본 바이트를 그대로 돌려준다`() {
        val face = png(8, 8) { fill(it, 0..7, 0..7, SKIN) }
        assertSame(face, AvatarImages.faceForRunningAmount(face, 0))
    }

    @Test
    fun `몸 텍스처는 맨몸 바탕에만 톤을 주고 의상 레이어는 원래 색으로 위에 얹는다`() {
        val body = png(256, 768) { bitmap -> fill(bitmap, 0..255, 0..765, SKIN) }
        val shorts = png(256, 768) { bitmap -> fill(bitmap, 0..255, 0..200, SHIRT) }
        val rendered =
            decode(
                OutfitLayerComposer.compose(
                    body,
                    listOf(OutfitPart.BOTTOM to shorts),
                    amount = 100,
                    width = 256,
                    height = 768
                )
            )

        assertEquals(SHIRT, rendered.getPixel(10, 10), "옷")
        assertNear(Color.rgb(214, 144, 116), rendered.getPixel(120, 350), "피부(가장 어두운 톤의 40%)")
        rendered.recycle()
    }

    @Test
    fun `생성 얼굴 피부색에 맨몸 피부만 맞추고 속옷 회색은 중앙값에 넣지 않는다`() {
        val source = png(256, 768) { bitmap ->
            fill(bitmap, 0..255, 0..300, Color.rgb(205, 208, 214))
            fill(bitmap, 0..255, 301..765, SKIN)
        }
        val matched = decode(GeneratedAvatarLayers.matchBody(source, Color.rgb(200, 150, 120)))

        assertNear(Color.rgb(200, 150, 120), matched.getPixel(120, 500), "피부는 생성 얼굴 피부색")
        matched.recycle()
    }

    @Test
    fun `정면 헤어 고유색은 톤을 거치지 않고 얼굴·헤어 경계는 러닝량 캐시를 다시 쓴다`() {
        val face = png(32, 32) { fill(it, 0..31, 0..31, SKIN) }
        val hair = png(32, 32) { fill(it, 0..31, 0..3, HAIR) }
        val request = AvatarToneRequest(
            version = 1,
            identity = 1,
            gender = AvatarGender.FEMALE,
            amount = 60,
            outfit = AvatarOutfit(),
            shape = AvatarBodyShape.BASE,
            face = face,
            hair = hair,
            frontFullness = null,
            runFullness = null,
            sideLayers = null,
            skinAssets = emptyMap(),
            generatedSkinColor = null
        )
        val first = AvatarTextureComposer.compose(request, AvatarToneCache())
        val firstHair = decode(checkNotNull(first.hair))
        assertEquals(HAIR, firstHair.getPixel(10, 1), "헤어 고유색은 톤을 거치지 않는다")
        firstHair.recycle()
        assertNotSame(face, checkNotNull(first.face).bytes, "얼굴은 톤을 적용한 새 텍스처")
        val still = AvatarToneRequest(
            version = 3,
            identity = 1,
            gender = AvatarGender.FEMALE,
            amount = 0,
            outfit = AvatarOutfit(),
            shape = AvatarBodyShape.BASE,
            face = face,
            hair = hair,
            frontFullness = null,
            runFullness = null,
            sideLayers = null,
            skinAssets = emptyMap(),
            generatedSkinColor = null
        )
        assertSame(hair, AvatarTextureComposer.compose(still, AvatarToneCache()).hair, "러닝량 0 헤어는 원본")

        val cached = AvatarTextureComposer.compose(request, first.toCache(AvatarToneCache()))
        assertSame(first.face, cached.face, "같은 러닝량이면 다시 계산하지 않는다")
        val changed = AvatarTextureComposer.compose(
            AvatarToneRequest(
                version = 2,
                identity = 1,
                gender = AvatarGender.FEMALE,
                amount = 80,
                outfit = AvatarOutfit(),
                shape = AvatarBodyShape.BASE,
                face = face,
                hair = hair,
                frontFullness = null,
                runFullness = null,
                sideLayers = null,
                skinAssets = emptyMap(),
                generatedSkinColor = null
            ),
            first.toCache(AvatarToneCache())
        )
        assertEquals(80, checkNotNull(changed.face).amount)
    }

    @Test
    fun `러닝량 100 - 얼굴과 맞닿은 헤어 경계의 피부 섞인 색만 섞인 몫만큼 어두워지고 헤어 고유색과 먼 곳은 그대로다(DV-5)`() {
        // 얼굴은 8행부터 불투명 피부, 헤어는 0~7행(얼굴과 겹치지 않음, 내장 머리와 같은 분할). 7행은 피부가 반쯤 섞인 경계,
        // 0행 왼쪽 끝은 얼굴에서 먼 같은 색(밝은 머리카락 끝)이다.
        val face = png(32, 32) { fill(it, 0..31, 8..31, SKIN) }
        val hair = png(32, 32) { bitmap ->
            fill(bitmap, 0..31, 0..6, HAIR)
            fill(bitmap, 0..31, 7..7, FRINGE)
            fill(bitmap, 0..1, 0..0, FRINGE)
        }
        val toned = decode(AvatarImages.hairForRunningAmount(hair, face, amount = 100))
        val fringe = toned.getPixel(16, 7)
        // 경계: 헤어 반 + 톤을 적용한 피부 반(R×0.88, G×0.80, B×0.728)
        assertNear(Color.rgb((90 + 214) / 2, (60 + 144) / 2, (40 + 116) / 2), fringe, "경계는 피부 몫만 어둡다")
        assertEquals(HAIR, toned.getPixel(16, 3), "헤어 고유색")
        assertEquals(FRINGE, toned.getPixel(0, 0), "얼굴에서 먼 밝은 헤어는 그대로")
        toned.recycle()

        val composed = decode(AvatarImages.composeSideHead(SideHeadLayers(face, png(32, 32) {}, hair), amount = 100))
        assertNear(fringe, composed.getPixel(16, 7), "측면 머리도 같은 경계 처리")
        composed.recycle()
        assertSame(hair, AvatarImages.hairForRunningAmount(hair, face, amount = 0), "러닝량 0은 원본 바이트")
    }

    private fun assertNear(expected: Int, actual: Int, message: String) {
        val channels = listOf(Color::red, Color::green, Color::blue)
        assertTrue(
            channels.all { kotlin.math.abs(it(expected) - it(actual)) <= 2 } && Color.alpha(actual) == 255,
            "$message: 기대 ${Integer.toHexString(expected)}, 실제 ${Integer.toHexString(actual)}"
        )
    }

    private companion object {
        val SKIN = Color.rgb(243, 180, 160)
        val EYE = Color.rgb(30, 20, 20)
        val HAIR = Color.rgb(90, 60, 40)

        /** 헤어와 피부(SKIN)가 반씩 섞인 경계 색. */
        val FRINGE = Color.rgb((90 + 243) / 2, (60 + 180) / 2, (40 + 160) / 2)
        val SHIRT = Color.rgb(250, 250, 250)

        fun png(width: Int, height: Int, draw: (Bitmap) -> Unit): ByteArray {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

        fun decode(bytes: ByteArray): Bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }
}
