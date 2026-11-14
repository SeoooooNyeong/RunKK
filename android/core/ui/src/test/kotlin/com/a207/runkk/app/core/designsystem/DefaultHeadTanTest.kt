package com.a207.runkk.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 기본 캐릭터 얼굴 원도 러닝량만큼 피부를 그을린다(S15P31A207-300, PM 2026-11-14): 기본 머리 그림 위에 같은 구도의 피부 층
 * (avatar_default_head_*_skin)만 셀카와 같은 거르개로 덧그려 피부만 어둡게 하고 머리카락·눈은 그대로 둔다. 기본 캐릭터 키
 * ("default:…")는 여러 러너가 같은 값을 쓰므로 한 러너의 얼굴 원만 감싸 내려 준 톤([SelfFaceTone.includesDefaultFace])일 때만 쓴다.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultHeadTanTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `셀카 키 그을림은 그대로다 - 키가 같고 러닝량이 있을 때만`() {
        assertNotNull(SelfFaceTone("head:7:a", 50).skinFilterFor("head:7:a"))
        assertNull(SelfFaceTone("head:7:a", 50).skinFilterFor("head:8:b"), "키가 다르면 쓰지 않는다")
        assertNull(SelfFaceTone("head:7:a", 0).skinFilterFor("head:7:a"), "러닝량 0이면 원본")
        assertNull(SelfFaceTone().skinFilterFor(null))
    }

    @Test
    fun `기본 캐릭터 키는 한 러너 얼굴만 감싼 톤일 때만 그을린다`() {
        assertNotNull(SelfFaceTone("default:BEAR", 50, includesDefaultFace = true).skinFilterFor("default:BEAR"))
        assertNull(
            SelfFaceTone("default:BEAR", 50, includesDefaultFace = true).skinFilterFor("default:FOX"),
            "키가 다르면 쓰지 않는다"
        )
        assertNull(
            SelfFaceTone("default:BEAR", 0, includesDefaultFace = true).skinFilterFor("default:BEAR"),
            "러닝량 0이면 원본"
        )
        // 앱 전체에 내려 준 내 톤이 같은 기본 캐릭터를 쓰는 다른 러너 얼굴까지 그을리지 않는다.
        assertNull(SelfFaceTone("default:BEAR", 50).skinFilterFor("default:BEAR"))
    }

    @Test
    fun `기본 머리 피부 층은 성별·원 안팎마다 있다`() {
        assertEquals(R.drawable.avatar_default_head_male_skin, RunkkAvatarArt.defaultHeadSkinOf(AvatarCharacter.MALE))
        assertEquals(
            R.drawable.avatar_default_head_female_skin,
            RunkkAvatarArt.defaultHeadSkinOf(AvatarCharacter.FEMALE)
        )
        assertEquals(
            R.drawable.avatar_default_head_male_free_skin,
            RunkkAvatarArt.defaultHeadSkinOf(AvatarCharacter.MALE, isInCircle = false)
        )
        assertEquals(
            R.drawable.avatar_default_head_female_free_skin,
            RunkkAvatarArt.defaultHeadSkinOf(AvatarCharacter.FEMALE, isInCircle = false)
        )
    }

    @Test
    fun `러닝량 0이면 원본, 50·100이면 피부 픽셀만 어두워지고 머리카락 픽셀은 그대로다`() {
        AvatarCharacter.entries.forEach { character ->
            val plain = capture(character, null)
            val zero = capture(character, SelfFaceTone(KEY, 0, includesDefaultFace = true))
            val half = capture(character, SelfFaceTone(KEY, 50, includesDefaultFace = true))
            val full = capture(character, SelfFaceTone(KEY, 100, includesDefaultFace = true))
            val (skin, hair) = samplePoints(character)
            assertTrue(skin.size > 500 && hair.size > 500, "$character 표본: 피부 ${skin.size}, 머리카락 ${hair.size}")

            assertTrue(plain.sameAs(zero), "$character 러닝량 0이면 원본 그대로")
            skin.forEach { (x, y) ->
                val p = brightness(plain.getPixel(x, y))
                val h = brightness(half.getPixel(x, y))
                val f = brightness(full.getPixel(x, y))
                assertTrue(h < p && f < h, "$character 피부($x,$y)는 50에서 어둡고 100에서 더 어둡다: $p → $h → $f")
            }
            hair.forEach { (x, y) ->
                assertEquals(plain.getPixel(x, y), half.getPixel(x, y), "$character 머리카락($x,$y)은 50에도 그대로")
                assertEquals(plain.getPixel(x, y), full.getPixel(x, y), "$character 머리카락($x,$y)은 100에도 그대로")
            }
        }
    }

    @Test
    fun `키가 다르거나 앱 전체 내 톤이면 기본 얼굴은 원본 그대로다`() {
        val plain = capture(AvatarCharacter.MALE, null)
        assertTrue(
            plain.sameAs(capture(AvatarCharacter.MALE, SelfFaceTone("default:FOX", 100, includesDefaultFace = true)))
        )
        assertTrue(plain.sameAs(capture(AvatarCharacter.MALE, SelfFaceTone(KEY, 100))))
    }

    private var shown by mutableStateOf<Pair<AvatarCharacter, SelfFaceTone>?>(null)

    /** 기본 머리 원을 그림 크기 그대로(256px, 원본 512의 1/2로 줄여 읽는다) 그려 찍는다. 내용은 한 번만 놓고 값만 바꾼다. */
    private fun capture(character: AvatarCharacter, tone: SelfFaceTone?): Bitmap {
        if (shown == null) {
            compose.setContent {
                val side = with(LocalDensity.current) { SIDE.toDp() }
                shown?.let { (who, faceTone) ->
                    RunkkTheme(reduceMotion = true) {
                        CompositionLocalProvider(LocalSelfFaceTone provides faceTone) {
                            RunkkAvatarPortrait(KEY, emptyList(), Modifier.size(side).testTag(TAG), who)
                        }
                    }
                }
            }
        }
        shown = character to (tone ?: SelfFaceTone())
        compose.waitForIdle()
        return compose.onNodeWithTag(TAG).captureToImage().asAndroidBitmap()
    }

    /**
     * 피부 표본: 피부 층이 (거의) 불투명하고(내장 얼굴 층 알파가 250 안팎이다) 주변 3×3도 피부인 자리. 머리카락 표본: 기본 머리가 불투명한데 주변 3×3에 피부가 전혀 없는 자리.
     * 그림과 같은 배수(2)로 줄여 읽어 화면 픽셀과 1:1로 맞춘다.
     */
    private fun samplePoints(character: AvatarCharacter): Pair<List<Pair<Int, Int>>, List<Pair<Int, Int>>> {
        val resources = RuntimeEnvironment.getApplication().resources
        val options = BitmapFactory.Options().apply {
            inSampleSize = 2
            inScaled = false
        }
        val head = BitmapFactory.decodeResource(resources, RunkkAvatarArt.defaultHeadOf(character), options)
        val skin = BitmapFactory.decodeResource(resources, RunkkAvatarArt.defaultHeadSkinOf(character), options)
        val skinPoints = mutableListOf<Pair<Int, Int>>()
        val hairPoints = mutableListOf<Pair<Int, Int>>()
        for (y in 2 until SIDE - 2 step 3) {
            for (x in 2 until SIDE - 2 step 3) {
                val around = (-1..1).flatMap { dy -> (-1..1).map { dx -> Color.alpha(skin.getPixel(x + dx, y + dy)) } }
                when {
                    around.all { it >= SKIN_ALPHA } -> skinPoints += x to y
                    around.all { it == 0 } && Color.alpha(head.getPixel(x, y)) == 255 -> hairPoints += x to y
                }
            }
        }
        return skinPoints to hairPoints
    }

    private fun brightness(color: Int): Int = Color.red(color) + Color.green(color) + Color.blue(color)

    private companion object {
        const val KEY = "default:RABBIT"
        const val SIDE = 256
        const val SKIN_ALPHA = 240
        const val TAG = "default-head-tan"
    }
}
