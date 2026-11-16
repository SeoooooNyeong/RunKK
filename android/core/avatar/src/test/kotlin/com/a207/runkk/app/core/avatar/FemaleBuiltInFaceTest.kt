package com.a207.runkk.app.core.avatar

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * S15P31A207-306: 기본 여자 얼굴은 남자 얼굴 그림을 목 끝에 맞춰 옮긴 뒤 [SCALE]배로 줄인 것이다. 여자 몸체가 머리 그림을 남자보다
 * 약 10.8% 크게 그리므로, 그림을 그만큼 줄여야 화면에서 남녀 얼굴 크기가 같다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FemaleBuiltInFaceTest {
    @Test
    fun `대각선으로 외곽에 이어진 투명은 제외하고 닫힌 한 픽셀 구멍은 센다`() {
        val skin = IntArray(1024 * 1024)
        for (y in 500..530) {
            for (x in 500..530) skin[y * 1024 + x] = Color.WHITE
        }
        val head = skin.copyOf()
        for (point in 500..502) head[point * 1024 + point] = Color.TRANSPARENT
        head[515 * 1024 + 515] = Color.TRANSPARENT
        assertEquals(listOf(515 to 515), BuiltInHeadPixels(head).internalGaps(BuiltInHeadPixels(skin)))
    }

    @Test
    fun `여자 두 눈을 표시 배율로 환산하면 남자와 폭 차이가 1퍼센트 이내다`() {
        for (name in listOf("eyes_open", "eyes_half", "eyes_closed")) {
            val male = BuiltInHeadPixels.read("male", name).bounds().width()
            val female = BuiltInHeadPixels.read("female", name).bounds().width()
            val difference = abs(female / SCALE / male - 1f)
            assertTrue(difference <= .01f, "$name 남자 $male, 여자 ${female / SCALE}, 차이 $difference")
        }
    }

    @Test
    fun `정면 세 표정과 볼 세 단계 합성의 얼굴과 머리 접합부에 내부 투명 픽셀이 없다`() {
        val failures = buildList {
            for (eye in listOf("eyes_open", "eyes_half", "eyes_closed")) {
                for (fullness in 0..2) {
                    val skin = skin("", fullness)
                    val head = BuiltInHeadPixels.composite(
                        skin,
                        BuiltInHeadPixels.read("female", eye),
                        BuiltInHeadPixels.read("female", "hair")
                    )
                    val gaps = head.internalGaps(skin)
                    if (gaps.isNotEmpty()) add("$eye, 볼 $fullness: ${gaps.size}개 ${gaps.take(8)}")
                }
            }
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun `측면 볼 세 단계 합성의 얼굴과 귀와 목과 머리 사이에 내부 투명 픽셀이 없다`() {
        val failures = buildList {
            for (fullness in 0..2) {
                val skin = skin("side_", fullness)
                val head = BuiltInHeadPixels.composite(
                    skin,
                    BuiltInHeadPixels.read("female", "side_eyes"),
                    BuiltInHeadPixels.read("female", "side_hair")
                )
                val gaps = head.internalGaps(skin)
                if (gaps.isNotEmpty()) add("볼 $fullness: ${gaps.size}개 ${gaps.take(8)}")
            }
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun `실제 측면 목 등록 합성에도 내부 투명 픽셀이 없다`() {
        val assets = AvatarVariantAssets.loadBuiltIn(RuntimeEnvironment.getApplication().assets, AvatarGender.FEMALE)
        val layers = assets.sideLayers
        val head = BuiltInHeadPixels.decode(AvatarImages.composeSideHead(layers, 0))
        val blank = BuiltInHeadPixels.transparentPng()
        // 같은 목 등록으로 변환한 피부만 판정 범위로 써, 줄어든 목 아래 머리카락 틈은 제외한다.
        val skin = BuiltInHeadPixels.decode(
            AvatarImages.composeSideHead(SideHeadLayers(layers.face, blank, blank, layers.neckRegistration), 0)
        )
        val neck = SideNeckRegistration.FEMALE
        assertTrue(skin.bounds().bottom <= neck.endY - neck.offsetY + 3f, "판정 범위도 줄어든 목이다")
        val failures = buildList {
            for (fullness in 0..2) {
                val cheek = if (fullness == 0) null else BuiltInHeadPixels.read("female", "side_fullness_$fullness")
                val composed = if (cheek == null) head else BuiltInHeadPixels.composite(head, cheek)
                val region = if (cheek == null) skin else BuiltInHeadPixels.composite(skin, cheek)
                val gaps = composed.internalGaps(region)
                if (gaps.isNotEmpty()) add("볼 $fullness: ${gaps.size}개 ${gaps.take(8)}")
            }
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun `정면과 측면 입에는 남자에 없는 입술 색이 없다`() {
        assertFaceColors("face", Rect(450, 680, 620, 740))
        assertFaceColors("side_face", Rect(840, 670, 930, 750))
    }

    @Test
    fun `정면과 측면 귀 주변에는 남자 얼굴에 없는 장신구 색이 없다`() {
        assertFaceColors("face", Rect(210, 520, 330, 700))
        assertFaceColors("face", Rect(750, 510, 860, 700))
        assertFaceColors("side_face", Rect(450, 510, 610, 700))
    }

    @Test
    fun `귀와 머리카락 합성에도 금색과 유색 장신구 색이 없다`() {
        val regions = mapOf(
            "" to listOf(Rect(190, 510, 350, 725), Rect(730, 500, 880, 725)),
            "side_" to listOf(Rect(440, 500, 630, 725))
        )
        for ((prefix, ears) in regions) {
            val head = BuiltInHeadPixels.composite(
                BuiltInHeadPixels.read("female", "${prefix}face"),
                BuiltInHeadPixels.read("female", "${prefix}hair")
            )
            val jewelryColors = ears.flatMap(head::colors).filter { color ->
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                // 피부와 갈색 머리 밖의 밝은 금색, 녹색, 파란색 장신구를 검출한다.
                hsv[0] in 35f..330f && hsv[1] > .25f && hsv[2] > .4f
            }
            assertEquals(emptyList(), jewelryColors.take(8), "$prefix 귀 주변 장신구 색")
        }
    }

    @Test
    fun `정면 머리의 눈과 목 중심은 같은 좌우 대칭 축에 놓인다`() {
        val face = BuiltInHeadPixels.read("female", "face")
        val neckAxis = face.rowCenter(face.bounds().bottom - 1)
        val hairAxis = BuiltInHeadPixels.read("female", "hair").bounds().exactCenterX()
        assertTrue(abs(hairAxis - neckAxis) <= 3f, "머리 축 $hairAxis, 목 축 $neckAxis")
        assertTrue(abs(face.bounds().exactCenterX() - neckAxis) <= 3f, "얼굴과 목의 대칭 축")
        for (eye in listOf("eyes_open", "eyes_half", "eyes_closed")) {
            val eyeAxis = BuiltInHeadPixels.read("female", eye).bounds().exactCenterX()
            assertTrue(abs(eyeAxis - neckAxis) <= 3f, "$eye 눈 축 $eyeAxis, 목 축 $neckAxis")
        }
        assertTrue(abs(neckAxis - HeadTarget.FEMALE_FRONT.centerX) <= 3f)
    }

    @Test
    fun `여자 셀카 정면 목표는 그림의 턱과 정수리와 목 중심에서 3픽셀 이내다`() {
        val face = BuiltInHeadPixels.read("female", "face")
        val neckAxis = face.rowCenter(face.bounds().bottom - 1)
        // 입 아래에서 목 끝 40px 위까지, 중심 21px의 마지막 어두운 턱 윤곽선을 찾는다.
        val chin = (700 until face.bounds().bottom - 40).last { y ->
            (neckAxis.toInt() - 10..neckAxis.toInt() + 10).map { Color.red(face[it, y]) }.average() < 180
        }
        assertTarget(HeadTarget.FEMALE_FRONT, chin.toFloat(), neckAxis, "hair")
    }

    @Test
    fun `여자 셀카 측면 목표는 그림의 턱과 정수리와 목 중심에서 3픽셀 이내다`() {
        val face = BuiltInHeadPixels.read("female", "side_face")
        val neckAxis = face.rowCenter(face.bounds().bottom - 1)
        // 목보다 오른쪽으로 돌출된 턱의 마지막 불투명 줄을 잰다.
        val chin = face.pixels.indices.filter {
            it % 1024 > neckAxis + 100 && face.pixels[it] ushr 24 >= 128
        }.maxOf { it / 1024 }
        assertTarget(HeadTarget.FEMALE_SIDE, chin.toFloat(), neckAxis, "side_hair")
    }

    private fun assertTarget(target: HeadTarget, chin: Float, neck: Float, hair: String) {
        val crown = BuiltInHeadPixels.read("female", hair).bounds().top.toFloat()
        assertTrue(abs(target.chinY - chin) <= 3f, "$target 턱 기대 ${target.chinY}, 그림 $chin")
        assertTrue(abs(target.crownY - crown) <= 3f, "$target 정수리 기대 ${target.crownY}, 그림 $crown")
        assertTrue(abs(target.centerX - neck) <= 3f, "$target 목 중심 기대 ${target.centerX}, 그림 $neck")
    }

    private fun skin(prefix: String, fullness: Int): BuiltInHeadPixels {
        val face = BuiltInHeadPixels.read("female", "${prefix}face")
        return if (fullness == 0) {
            face
        } else {
            BuiltInHeadPixels.composite(
                face,
                BuiltInHeadPixels.read("female", "${if (prefix.isEmpty()) "front_" else prefix}fullness_$fullness")
            )
        }
    }

    private fun assertFaceColors(name: String, region: Rect) {
        val female = BuiltInHeadPixels.read("female", name).colors(region)
        val center = if (name == "face") 534f to 520f else 651f to 582f
        fun sourceX(x: Int): Int = ((x - center.first) / SCALE + center.second).toInt()
        fun sourceY(y: Int): Int = ((y - 863f) / SCALE + 930f).toInt()
        val male = BuiltInHeadPixels.read("male", name).colors(
            Rect(sourceX(region.left), sourceY(region.top), sourceX(region.right), sourceY(region.bottom))
        )
        assertTrue(female.isNotEmpty() && male.isNotEmpty(), "$name $region 검사할 피부색이 없다")
        // 보간에 따른 채널 차이 16까지만 허용한다. 피부와 공통 입 윤곽선 색은 남자 표본에 포함된다.
        val addedColors = female.filter { color -> male.none { BuiltInHeadPixels.colorDistance(color, it) <= 16 } }
        assertEquals(emptyList(), addedColors.take(8), "$name $region 남자에 없는 색 ${addedColors.size}종")
        assertTrue(female.maxOf(::saturation) <= male.maxOf(::saturation) + .03f, "$name $region 채도 증가")
    }

    private fun saturation(color: Int): Float {
        val channels = listOf(Color.red(color), Color.green(color), Color.blue(color))
        val max = channels.max()
        return if (max == 0) 0f else (max - channels.min()).toFloat() / max
    }

    @Test
    fun `정면 눈과 볼은 남자 그림의 0점903배다`() {
        for (name in listOf("eyes_open", "eyes_half", "eyes_closed", "front_fullness_1", "front_fullness_2")) {
            assertScaled(name)
        }
    }

    @Test
    fun `측면 눈과 볼도 같은 배율이다`() {
        for (name in listOf("side_eyes", "side_fullness_1", "side_fullness_2")) {
            assertScaled(name)
        }
    }

    @Test
    fun `목 끝과 목 맞춤값은 몸체 자리 그대로다`() {
        // 목 맞춤은 목 길이 72를 전제로 한다(SideNeckRegistrationTest). 그림을 줄여도 구간은 그대로 둔다.
        assertEquals(791f, SideNeckRegistration.FEMALE.startY)
        assertEquals(863f, SideNeckRegistration.FEMALE.endY)
        assertEquals(651f, SideNeckRegistration.FEMALE.centerX)
        val front = bounds("female", "face")
        val side = bounds("female", "side_face")
        assertTrue(abs(front.bottom - 865) <= 3, "정면 목 끝 ${front.bottom}")
        assertTrue(abs(side.bottom - 865) <= 3, "측면 목 끝 ${side.bottom}")
    }

    private fun assertScaled(name: String) {
        val male = bounds("male", name)
        val female = bounds("female", name)
        val width = female.width.toFloat() / male.width
        val height = female.height.toFloat() / male.height
        assertTrue(abs(width - SCALE) < TOLERANCE, "$name 가로 배율 $width")
        assertTrue(abs(height - SCALE) < TOLERANCE, "$name 세로 배율 $height")
    }

    private class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left + 1
        val height get() = bottom - top + 1
    }

    /** 불투명도가 절반을 넘는 픽셀의 경계 상자. */
    private fun bounds(gender: String, name: String): Bounds {
        val bitmap = RuntimeEnvironment.getApplication().assets.open("avatars/$gender/$name.png").use {
            checkNotNull(BitmapFactory.decodeStream(it))
        }
        try {
            assertEquals(1024, bitmap.width)
            assertEquals(1024, bitmap.height)
            val pixels = IntArray(1024 * 1024).also { bitmap.getPixels(it, 0, 1024, 0, 0, 1024, 1024) }
            var left = 1024
            var top = 1024
            var right = -1
            var bottom = -1
            for (index in pixels.indices) {
                if (pixels[index] ushr 24 > 127) {
                    val x = index % 1024
                    val y = index / 1024
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            return Bounds(left, top, right, bottom)
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val SCALE = .903f
        const val TOLERANCE = .02f
    }
}
