package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * 의상 레이어 합성(D-성-110·112, OUTFIT_SPEC.md 5.3·5.4·5.6). 실제 자산(assets/outfits)으로 슬롯 텍스처를 만들어 픽셀을 본다.
 * Rive 그림은 Robolectric에 나오지 않으므로 riv에 넣는 합성 텍스처를 검사한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutfitCompositeTest {
    private val root = File("src/main/assets")

    @BeforeTest
    fun install() = OutfitLibrary.install(OutfitFiles { File(root, it).takeIf(File::isFile)?.readBytes() })

    private val catalog get() = checkNotNull(OutfitLibrary.catalog)

    @Test
    fun `겹침 순서는 신발, 하의, 상의이고 묶음이 없는 키나 파일은 그 부위 base_*로 그린다`() {
        val all = { _: String -> true }
        assertEquals(
            listOf(
                "outfits/shoes_carbon_racer/female_stand.png",
                "outfits/bottom_leggings/female_stand.png",
                "outfits/top_crew_tee/female_stand.png"
            ),
            OutfitLayerComposer.layerFiles(
                catalog,
                OutfitSlot.STAND,
                AvatarGender.FEMALE,
                AvatarBodyShape.BASE,
                AvatarOutfit(2, 2, 3),
                all
            )
        )
        assertEquals(
            listOf("outfits/top_cherry_vest/male_run_arm.png"),
            OutfitLayerComposer.layerFiles(
                catalog,
                OutfitSlot.RUN_ARM,
                AvatarGender.MALE,
                AvatarBodyShape.CHANGE_2,
                AvatarOutfit(top = 6),
                all
            ),
            "체형 전용 파일이 없으면 기준 파일"
        )
        assertEquals("base_top", catalog.bundle(OutfitPart.TOP, "hat_beanie")?.assetKey, "모르는 키")
        assertEquals("base_top", catalog.bundle(OutfitPart.TOP, "bottom_leggings")?.assetKey, "다른 부위 키")
        val missing = OutfitLayerComposer.layerFiles(
            catalog,
            OutfitSlot.RUN_LEG,
            AvatarGender.FEMALE,
            AvatarBodyShape.BASE,
            AvatarOutfit(bottom = 4)
        ) { !it.contains("bottom_wide_pants") }
        assertEquals(listOf("outfits/base_bottom/female_run_leg.png"), missing, "파일을 못 찾으면 base_*")
    }

    @Test
    fun `피부톤은 맨몸 바탕에만 걸리고 옷 픽셀은 러닝량 100에서도 그대로다(21가지 모두)`() {
        OutfitCatalog.LEGACY_KEYS.forEach { (part, keys) ->
            keys.indices.forEach { id ->
                val outfit = when (part) {
                    OutfitPart.TOP -> AvatarOutfit(top = id)
                    OutfitPart.BOTTOM -> AvatarOutfit(bottom = id)
                    OutfitPart.SHOES -> AvatarOutfit(shoes = id)
                }
                OutfitSlot.entries.filter { part in it.parts }.forEach { slot ->
                    assertToneOnlyOnBody(slot, AvatarGender.FEMALE, outfit, "${keys[id]} ${slot.fileName}")
                }
            }
        }
    }

    @Test
    fun `소매 끝 아래 팔에 톤을 받지 않은 밝은 피부 띠가 없다(DV-성-11)`() {
        // 기본 반팔·민소매·카라티: 서기 아틀라스의 양팔(x 10~130·382~502, 소매 끝 아래 y 150~300).
        listOf(0, 1, 2).forEach { top ->
            listOf(AvatarGender.FEMALE, AvatarGender.MALE).forEach { gender ->
                val outfit = AvatarOutfit(top = top)
                val light = compose(OutfitSlot.STAND, gender, outfit, 0)
                val dark = compose(OutfitSlot.STAND, gender, outfit, 100)
                val layer = decodeLayer(OutfitSlot.STAND, gender, outfit)
                var checked = 0
                for (y in 150..300) {
                    for (x in (10..130) + (382..502)) {
                        val skin = layer[y * OutfitSlot.STAND.width + x] ushr 24 == 0 &&
                            Color.alpha(light.getPixel(x, y)) >= 250
                        if (!skin) continue
                        checked++
                        assertToned(light.getPixel(x, y), dark.getPixel(x, y), "상의 $top $gender ($x, $y)")
                    }
                }
                assertTrue(checked > 500, "검사한 팔 피부 픽셀 $checked")
                light.recycle()
                dark.recycle()
            }
        }
    }

    @Test
    fun `기본 의상 러닝량 0 합성은 승인 기본 텍스처의 팔 안쪽 윤곽과 손을 그대로 둔다`() {
        val approved = BitmapFactory.decodeFile("src/main/res/raw/avatar_legacy_standing_body.png")
        val composed = compose(OutfitSlot.STAND, AvatarGender.FEMALE, AvatarOutfit(), 0)
        // 손 칸(Preserved approved hands, x < 100 또는 x > 413, y 305~410)과 팔(x 10~140·372~502, y 160~300).
        val regions = listOf(0..99 to 305..410, 414..511 to 305..410, 10..140 to 160..300, 372..502 to 160..300)
        var outline = 0
        var kept = 0
        var same = 0
        var total = 0
        regions.forEach { (xs, ys) ->
            for (y in ys) {
                for (x in xs) {
                    val a = approved.getPixel(x, y)
                    if (Color.alpha(a) < 250) continue
                    val c = composed.getPixel(x, y)
                    total++
                    if (near(a, c, 6)) same++
                    if (luma(a) < 110) {
                        outline++
                        if (luma(c) < 140) kept++
                    }
                }
            }
        }
        assertTrue(outline > 400 && kept >= outline * 98 / 100, "윤곽 보존 $kept / $outline")
        assertTrue(same >= total * 90 / 100, "승인 픽셀 보존 $same / $total")
        approved.recycle()
        composed.recycle()
    }

    private fun luma(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

    @Test
    fun `남자 서기 표면은 묶음의 maleTopSurface, 하의·신발 1이다`() {
        val expected = listOf(1f, 1f, 1f, 2f, 2f, 2f, 2f)
        assertEquals(expected, (0..6).map { RiveAvatarScene.topSurface(AvatarGender.MALE, it) })
        assertEquals(1f, RiveAvatarScene.bottomSurface(0))
        assertEquals(1f, RiveAvatarScene.shoesSurface(0))
    }

    private fun assertToneOnlyOnBody(slot: OutfitSlot, gender: AvatarGender, outfit: AvatarOutfit, message: String) {
        val light = compose(slot, gender, outfit, 0)
        val dark = compose(slot, gender, outfit, 100)
        val layers = decodeLayer(slot, gender, outfit)
        var cloth = 0
        for (y in 0 until slot.height step 2) {
            for (x in 0 until slot.width step 2) {
                if (layers[y * slot.width + x] ushr 24 >= 254) {
                    cloth++
                    assertTrue(near(light.getPixel(x, y), dark.getPixel(x, y), 3), "$message 옷 ($x, $y)")
                }
            }
        }
        assertTrue(cloth > 0, "$message 옷 픽셀")
        light.recycle()
        dark.recycle()
    }

    /** 그 모습의 의상 레이어만 겹친 알파(맨몸 없이). */
    private fun decodeLayer(slot: OutfitSlot, gender: AvatarGender, outfit: AvatarOutfit): IntArray {
        val files = OutfitLayerComposer.layerFiles(catalog, slot, gender, AvatarBodyShape.BASE, outfit) {
            OutfitLibrary.read(it) != null
        }
        val bytes = OutfitLayerComposer.compose(
            null,
            files.map {
                OutfitLibrary.read(it)!!
            },
            0,
            slot.width,
            slot.height
        )
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        return IntArray(slot.width * slot.height).also {
            bitmap.getPixels(it, 0, slot.width, 0, 0, slot.width, slot.height)
            bitmap.recycle()
        }
    }

    private fun compose(slot: OutfitSlot, gender: AvatarGender, outfit: AvatarOutfit, amount: Int): Bitmap {
        val files = OutfitLayerComposer.layerFiles(catalog, slot, gender, AvatarBodyShape.BASE, outfit) {
            OutfitLibrary.read(it) != null
        }
        val body = slot.bodyFile(gender)?.let { OutfitLibrary.read(it) }
        val bytes = OutfitLayerComposer.compose(
            body,
            files.map {
                OutfitLibrary.read(it)!!
            },
            amount,
            slot.width,
            slot.height
        )
        return checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    /** 러닝량 100 톤(R×0.70, G×0.50, B×0.32). 반투명 가장자리 반올림을 감안해 채널마다 3까지. */
    private fun assertToned(source: Int, actual: Int, message: String) {
        val expected = Color.rgb(
            (Color.red(source) * .70f).toInt(),
            (Color.green(source) * .50f).toInt(),
            (Color.blue(source) * .32f).toInt()
        )
        assertTrue(
            near(expected, actual, 3),
            "$message: 기대 ${Integer.toHexString(expected)}, 실제 ${Integer.toHexString(actual)}"
        )
    }

    private fun near(a: Int, b: Int, tolerance: Int): Boolean =
        listOf(Color::red, Color::green, Color::blue).all { abs(it(a) - it(b)) <= tolerance }
}
