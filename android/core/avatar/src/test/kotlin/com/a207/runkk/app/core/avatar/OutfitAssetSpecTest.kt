package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * 의상 자산 규격 검사(OUTFIT_SPEC.md 6절 여덟 항목, D-성-114). 파이프라인(tools/outfit-pipeline `check`)과 같은 규칙을 빌드 때 다시 본다.
 * 자산은 무손실 WebP이고 남녀 같은 파일은 index.json `shared`로 한 장을 함께 쓴다. 허용 영역 마스크는 파이프라인 `uv-mask`가 riv 메시
 * UV로 만든 templates/masks(H-성-1: riv가 바뀌면 다시 만든다)다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutfitAssetSpecTest {
    private val assets = File("src/main/assets")
    private val outfits = File(assets, "outfits")
    private val masks = File("tools/outfit-pipeline/templates/masks")
    private val catalog = OutfitCatalog.parse(File(outfits, "index.json").readText())
    private val bundles = OutfitCatalog.LEGACY_KEYS.flatMap { (part, keys) -> keys.map { catalog.bundle(part, it)!! } }
    private val genders = listOf(AvatarGender.FEMALE, AvatarGender.MALE)

    @Test
    fun `1 목록의 키마다 폴더와 부위별 필수 파일(공용 포함)이 있고 목록에 없는 폴더가 없다`() {
        val folders = outfits.listFiles()!!.filter {
            it.isDirectory && it.name != OutfitCatalog.BODY
        }.map { it.name }.toSet()
        assertEquals(catalog.keys, folders)
        assertEquals(OutfitCatalog.LEGACY_KEYS.values.flatten().toSet(), catalog.keys, "기존 21가지(5.7)")
        bundles.forEach { bundle ->
            genders.forEach { gender ->
                slotsOf(bundle.part).forEach { slot ->
                    val path = bundle.file(gender, slot, AvatarBodyShape.BASE)
                    assertTrue(File(assets, path).isFile, "${bundle.assetKey} $gender $slot → $path")
                }
            }
            assertTrue(File(outfits, "${bundle.assetKey}/shop.webp").isFile, "${bundle.assetKey}/shop.webp")
        }
        genders.forEach { gender ->
            OutfitSlot.entries.forEach { slot ->
                listOfNotNull(catalog.bodyFile(gender, slot), catalog.cutFile(gender, slot)).forEach { path ->
                    val image = read(File(assets, path))
                    assertEquals(slot.width to slot.height, image.width to image.height, "맨몸 바탕·자르기 칸 $path")
                    image.recycle()
                }
            }
        }
    }

    @Test
    fun `2~4 파일 크기, 금지 영역 알파 0, 알파는 UV 허용 영역 안에만 1% 이상(남녀 템플릿 각각)`() {
        bundles.forEach { bundle ->
            genders.forEach { gender ->
                slotsOf(bundle.part).forEach { slot -> checkLayer(bundle, gender, slot) }
            }
        }
    }

    private fun checkLayer(bundle: OutfitBundle, gender: AvatarGender, slot: OutfitSlot) {
        val file = File(assets, bundle.file(gender, slot, AvatarBodyShape.BASE))
        val image = read(file)
        assertEquals(slot.width to slot.height, image.width to image.height, "크기 ${file.path}")
        val template = templateOf(gender, slot, bundle)
        val allowed = read(File(masks, "${template}_${bundle.part}.png"))
        val uv = read(File(masks, "$template.png"))
        var drawn = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getPixel(x, y) ushr 24 == 0) continue
                drawn++
                check(on(uv, x, y)) { "4번 UV 밖 ($x, $y) ${file.path} $gender" }
                check(on(allowed, x, y)) { "3번 금지 영역 ($x, $y) ${file.path} $gender" }
            }
        }
        assertTrue(drawn >= image.width * image.height / 100, "4번 1% 이상 ${file.path}")
        listOf(image, allowed, uv).forEach(Bitmap::recycle)
    }

    @Test
    fun `5 상점 그림은 512 정사각, 불투명 긴 변 420~460, 가운데에서 8px 안`() {
        bundles.forEach { bundle ->
            val shop = read(File(outfits, "${bundle.assetKey}/shop.webp"))
            assertEquals(512 to 512, shop.width to shop.height)
            var left = 512
            var top = 512
            var right = -1
            var bottom = -1
            for (y in 0 until 512) {
                for (x in 0 until 512) {
                    if (shop.getPixel(x, y) ushr 24 != 0) {
                        left = minOf(left, x)
                        top = minOf(top, y)
                        right = maxOf(right, x + 1)
                        bottom = maxOf(bottom, y + 1)
                    }
                }
            }
            assertTrue(maxOf(right - left, bottom - top) in 420..460, "긴 변 ${bundle.assetKey}")
            assertTrue(abs((left + right) / 2.0 - 256) <= 8, "가로 가운데 ${bundle.assetKey}")
            assertTrue(abs((top + bottom) / 2.0 - 256) <= 8, "세로 가운데 ${bundle.assetKey}")
            shop.recycle()
        }
    }

    @Test
    fun `6~8 파일 이름 규칙(webp), 묶음 1_5MB 이하, 상의 maleTopSurface 1·2와 체형 파일, 공용 이름은 파일 없음`() {
        val pattern = Regex("""^(female|male)_(stand|run_body|run_arm|run_leg|run_shoe)(_b1|_b2)?\.webp$""")
        bundles.forEach { bundle ->
            val files = File(outfits, bundle.assetKey).listFiles()!!
            files.filter { it.name != "shop.webp" }.forEach { file ->
                val match = pattern.matchEntire(file.name)
                assertTrue(match != null && slotOf(match.groupValues[2]).parts.contains(bundle.part), "6번 ${file.path}")
            }
            assertTrue(files.sumOf { it.length() } <= 1_572_864, "7번 ${bundle.assetKey}")
            if (bundle.part == OutfitPart.TOP) assertTrue(bundle.maleTopSurface in 1..2, "8번 ${bundle.assetKey}")
            bundle.bodyOverrides.forEach { name ->
                assertTrue(File(outfits, "${bundle.assetKey}/${bundle.shared[name] ?: name}.webp").isFile, "8번 $name")
            }
            bundle.shared.keys.forEach { alias ->
                assertTrue(!File(outfits, "${bundle.assetKey}/$alias.webp").exists(), "공용 이름 $alias 는 파일이 없다")
            }
        }
        // 지금 7종의 표면: 상의 0·1·2 → 1, 3~6 → 2(5.2).
        assertEquals(
            listOf(1, 1, 1, 2, 2, 2, 2),
            OutfitCatalog.LEGACY_KEYS.getValue(OutfitPart.TOP).map {
                catalog.bundle(OutfitPart.TOP, it)!!.maleTopSurface
            }
        )
    }

    private fun slotsOf(part: OutfitPart) = OutfitSlot.entries.filter { part in it.parts }

    private fun slotOf(fileName: String) = OutfitSlot.entries.first { it.fileName == fileName }

    private fun templateOf(gender: AvatarGender, slot: OutfitSlot, bundle: OutfitBundle): String {
        val g = gender.fileName
        return when {
            slot != OutfitSlot.STAND -> "${g}_${slot.fileName}"
            gender == AvatarGender.FEMALE -> "female_stand"
            bundle.part == OutfitPart.TOP -> "male_stand_s${bundle.maleTopSurface}"
            else -> "male_stand_s1"
        }
    }

    private fun read(file: File): Bitmap = checkNotNull(BitmapFactory.decodeFile(file.path)) {
        "unreadable ${file.path}"
    }

    /** 1비트 마스크에서 흰 칸인가. */
    private fun on(mask: Bitmap, x: Int, y: Int): Boolean = mask.getPixel(x, y) and 0xFFFFFF != 0
}
