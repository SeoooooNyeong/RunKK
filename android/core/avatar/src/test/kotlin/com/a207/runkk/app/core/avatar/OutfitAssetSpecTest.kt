package com.a207.runkk.app.core.avatar

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 의상 자산 규격 검사(OUTFIT_SPEC.md 5.5 여덟 항목, D-성-114). 파이프라인(tools/outfit-pipeline `check`)과 같은 규칙을 빌드 때 다시 본다.
 * 허용 영역 마스크는 파이프라인 `uv-mask`가 riv 메시 UV로 만든 templates/masks(H-성-1: riv가 바뀌면 다시 만든다)다.
 */
class OutfitAssetSpecTest {
    private val outfits = File("src/main/assets/outfits")
    private val masks = File("tools/outfit-pipeline/templates/masks")
    private val catalog = OutfitCatalog.parse(File(outfits, "index.json").readText())
    private val bundles = OutfitCatalog.LEGACY_KEYS.flatMap { (part, keys) -> keys.map { catalog.bundle(part, it)!! } }

    @Test
    fun `1 목록의 키마다 폴더와 부위별 필수 파일이 있고 목록에 없는 폴더가 없다`() {
        val folders = outfits.listFiles()!!.filter {
            it.isDirectory && it.name != OutfitCatalog.BODY
        }.map { it.name }.toSet()
        assertEquals(catalog.keys, folders)
        assertEquals(OutfitCatalog.LEGACY_KEYS.values.flatten().toSet(), catalog.keys, "기존 21가지(5.7)")
        bundles.forEach { bundle ->
            requiredFiles(bundle.part).forEach { name ->
                assertTrue(File(outfits, "${bundle.assetKey}/$name").isFile, "${bundle.assetKey}/$name")
            }
        }
        listOf("female", "male").forEach { g ->
            listOf("stand", "run_body", "run_arm", "run_leg").forEach { slot ->
                val body = read(File(outfits, "${OutfitCatalog.BODY}/${g}_$slot.png"))
                val size = OutfitSlot.entries.first { it.fileName == slot }
                assertEquals(size.width to size.height, body.width to body.height, "맨몸 바탕 ${g}_$slot")
            }
        }
    }

    @Test
    fun `2~4 파일 크기·RGBA, 금지 영역 알파 0, 알파는 UV 허용 영역 안에만 1% 이상`() {
        bundles.forEach { bundle ->
            layerFiles(bundle).forEach { file ->
                val slot = slotOf(file.name)
                val image = read(file)
                assertEquals(slot.width to slot.height, image.width to image.height, "크기 ${file.path}")
                assertTrue(
                    image.colorModel.hasAlpha() && image.colorModel.componentSize.all {
                        it == 8
                    },
                    "RGBA ${file.path}"
                )
                val template = templateOf(file.name, bundle)
                val allowed = read(File(masks, "${template}_${bundle.part}.png"))
                val uv = read(File(masks, "$template.png"))
                var drawn = 0
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) {
                        if (image.getRGB(x, y) ushr 24 == 0) continue
                        drawn++
                        check(on(uv, x, y)) { "4번 UV 밖 ($x, $y) ${file.path}" }
                        check(on(allowed, x, y)) { "3번 금지 영역 ($x, $y) ${file.path}" }
                    }
                }
                assertTrue(drawn >= image.width * image.height / 100, "4번 1% 이상 ${file.path}")
            }
        }
    }

    @Test
    fun `5 상점 그림은 512 정사각, 불투명 긴 변 420~460, 가운데에서 8px 안`() {
        bundles.forEach { bundle ->
            val shop = read(File(outfits, "${bundle.assetKey}/shop.png"))
            assertEquals(512 to 512, shop.width to shop.height)
            var (left, top, right, bottom) = listOf(512, 512, -1, -1)
            for (y in 0 until 512) {
                for (x in 0 until 512) {
                    if (shop.getRGB(x, y) ushr 24 != 0) {
                        left = minOf(left, x)
                        top = minOf(top, y)
                        right = maxOf(right, x + 1)
                        bottom = maxOf(bottom, y + 1)
                    }
                }
            }
            assertTrue(maxOf(right - left, bottom - top) in 420..460, "긴 변 ${bundle.assetKey}")
            assertTrue(kotlin.math.abs((left + right) / 2.0 - 256) <= 8, "가로 가운데 ${bundle.assetKey}")
            assertTrue(kotlin.math.abs((top + bottom) / 2.0 - 256) <= 8, "세로 가운데 ${bundle.assetKey}")
        }
    }

    @Test
    fun `6~8 파일 이름 규칙, 묶음 1_5MB 이하, 상의 maleTopSurface 1·2와 체형 파일`() {
        val pattern = Regex("""^(female|male)_(stand|run_body|run_arm|run_leg|run_shoe)(_b1|_b2)?\.png$""")
        bundles.forEach { bundle ->
            val files = File(outfits, bundle.assetKey).listFiles()!!
            files.filter { it.name != "shop.png" }.forEach { file ->
                val match = pattern.matchEntire(file.name)
                assertTrue(match != null && slotOf(file.name).parts.contains(bundle.part), "6번 ${file.path}")
            }
            assertTrue(files.sumOf { it.length() } <= 1_572_864, "7번 ${bundle.assetKey}")
            if (bundle.part == OutfitPart.TOP) assertTrue(bundle.maleTopSurface in 1..2, "8번 ${bundle.assetKey}")
            bundle.bodyOverrides.forEach { assertTrue(File(outfits, "${bundle.assetKey}/$it.png").isFile, "8번 $it") }
        }
        // 지금 7종의 표면: 상의 0·1·2 → 1, 3~6 → 2(5.2).
        assertEquals(
            listOf(1, 1, 1, 2, 2, 2, 2),
            OutfitCatalog.LEGACY_KEYS.getValue(OutfitPart.TOP).map {
                catalog.bundle(OutfitPart.TOP, it)!!.maleTopSurface
            }
        )
    }

    private fun requiredFiles(part: OutfitPart): List<String> = listOf("female", "male").flatMap { g ->
        OutfitSlot.entries.filter { part in it.parts }.map { "${g}_${it.fileName}.png" }
    } + "shop.png"

    private fun layerFiles(bundle: OutfitBundle) =
        File(outfits, bundle.assetKey).listFiles()!!.filter { it.name != "shop.png" }

    private fun slotOf(name: String): OutfitSlot {
        val slot = name.removeSuffix(".png").substringAfter('_').removeSuffix("_b1").removeSuffix("_b2")
        return OutfitSlot.entries.first { it.fileName == slot }
    }

    private fun templateOf(name: String, bundle: OutfitBundle): String {
        val gender = name.substringBefore('_')
        val slot = slotOf(name)
        return when {
            slot != OutfitSlot.STAND -> "${gender}_${slot.fileName}"
            gender == "female" -> "female_stand"
            bundle.part == OutfitPart.TOP -> "male_stand_s${bundle.maleTopSurface}"
            else -> "male_stand_s1"
        }
    }

    private fun read(file: File): BufferedImage = checkNotNull(ImageIO.read(file)) { "unreadable ${file.path}" }

    /** 1비트 마스크에서 흰 칸인가. */
    private fun on(mask: BufferedImage, x: Int, y: Int): Boolean = mask.getRGB(x, y) and 0xFFFFFF != 0
}
