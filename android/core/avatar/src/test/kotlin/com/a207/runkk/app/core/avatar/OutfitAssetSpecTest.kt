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
 * 의상 자산 규격 검사(OUTFIT_SPEC.md 6절 열일곱 항목, D-성-114). 파이프라인(tools/outfit-pipeline `check`)과 같은 규칙을 빌드 때 다시 본다.
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
        // 소매 없는 상의의 달리기 팔은 비어 있다(파이프라인 spec.EMPTY_RUN_ARM_KEYS, 판정 round3)
        val emptyOk = file.parentFile?.name == "top_lime_singlet" && "run_arm" in file.name
        assertTrue(emptyOk || drawn >= image.width * image.height / 100, "4번 1% 이상 ${file.path}")
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

    @Test
    fun `9 옷 레이어에는 드러난 맨살이 없다(피부는 맨몸 바탕에서만 와 러닝량 톤이 걸린다)`() {
        val found = outfits.listFiles()!!.filter { it.isDirectory && it.name != OutfitCatalog.BODY }
            .flatMap { folder -> folder.listFiles()!!.filter { it.extension == "webp" && it.name != "shop.webp" } }
            .associate { file ->
                val bitmap = read(file)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                "${file.parentFile!!.name}/${file.name}" to ExposedSkin.count(pixels, bitmap.width, bitmap.height)
                    .also { bitmap.recycle() }
            }
            .filterValues { it > 0 }
        assertEquals(emptyMap(), found, "옷 레이어의 맨살 픽셀(외곽선 부스러기 제외)")
    }

    @Test
    fun `10 맨몸 바탕과 옷 레이어에 굵은 실루엣 밖 부스러기(뻗은 털, 떨어진 점)가 없다`() {
        val files = outfits.listFiles()!!.filter { it.isDirectory }
            .flatMap { folder ->
                folder.listFiles()!!.filter {
                    it.extension == "webp" && it.name != "shop.webp" && !it.name.endsWith("_cut.webp")
                }
            }
        val found = files.associate { file ->
            val bitmap = read(file)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            // 갈색 실오라기는 맨팔이 옷 가장자리에 닿는 반팔·민소매 상의(base_top 제외)의 서기·달리기 몸통만 본다(파이프라인 body.wisp_layer와 같다)
            val folder = file.parentFile!!.name
            val bundle = catalog.bundle(OutfitPart.TOP, folder)?.takeIf { it.assetKey == folder }
            val garment = bundle != null && bundle.maleTopSurface == 1 && !folder.startsWith("base_") &&
                Regex("""^(female|male)_(stand|run_body)(_b1|_b2)?\.webp$""").matches(file.name)
            "${file.parentFile!!.name}/${file.name}" to
                StrayPixels.count(pixels, bitmap.width, bitmap.height, garment, thin = folder != OutfitCatalog.BODY)
                    .also { bitmap.recycle() }
        }.filterValues { it > 0 }
        assertTrue(files.size > 40, "검사한 파일 ${files.size}")
        assertEquals(emptyMap(), found, "부스러기 픽셀(메시가 늘면 선으로 보인다, [임시:D-성-184])")
    }

    @Test
    fun `11 맨몸 서기 바탕의 허리·엉덩이 칸에는 속옷(무채색) 픽셀이 없다`() {
        genders.forEach { gender ->
            val bitmap = read(File(assets, catalog.bodyFile(gender, OutfitSlot.STAND)!!))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertEquals(
                0,
                StrayPixels.underwear(pixels, bitmap.width, bitmap.height),
                "$gender 하의 밖으로 비치는 속옷([임시:D-성-185])"
            )
            bitmap.recycle()
        }
    }

    @Test
    fun `12 하의 서기 레이어의 허리 겹침 띠에 하의 본체와 떨어진 조각이 없다`() {
        val found = bundles.filter { it.part == OutfitPart.BOTTOM }.associate { bundle ->
            val bitmap = read(File(assets, bundle.file(AvatarGender.FEMALE, OutfitSlot.STAND, AvatarBodyShape.BASE)))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bundle.assetKey to StrayPixels.looseWaist(pixels, bitmap.width, bitmap.height).also { bitmap.recycle() }
        }.filterValues { it > 0 }
        assertEquals(emptyMap(), found, "밑단과 허리 사이 맨몸 위 얼룩([임시:D-성-185])")
    }

    @Test
    fun `13 하의 서기 허리는 겹침 띠 위(y 296)까지 맨몸 실루엣을 덮는다`() {
        val body = read(File(assets, catalog.bodyFile(AvatarGender.FEMALE, OutfitSlot.STAND)!!))
        val bodyPixels = IntArray(body.width * body.height).also {
            body.getPixels(it, 0, body.width, 0, 0, body.width, body.height)
        }
        val inside = StrayPixels.bodyInside(bodyPixels, body.width, body.height)
        val found = bundles.filter { it.part == OutfitPart.BOTTOM }.associate { bundle ->
            val bitmap = read(File(assets, bundle.file(AvatarGender.FEMALE, OutfitSlot.STAND, AvatarBodyShape.BASE)))
            fun solid(x: Int, y: Int) = bitmap.getPixel(x, y) ushr 24 >= 128
            // 열마다 하의가 시작하는 행(파이프라인 body.waist_starts와 같다): 하의가 5행 이어지는 곳이 y 296~329에 있으면 거기서 위로 이어진
            // 천의 첫 행, 그런 열 사이 10px 안의 틈(허리 가운데 트인 곳)은 처음 불투명한 행(y 345까지, 없으면 345)
            val starts = sortedMapOf<Int, Int>()
            for (x in 100 until 412) {
                var run = (296 until 330).firstOrNull { r -> (r until r + 5).all { solid(x, it) } } ?: continue
                // 그 5행에서 위로 이어진 천의 첫 행(떨어진 점에서 시작하지 않는다)
                while (run > 296 && solid(x, run - 1)) run--
                starts[x] = run
            }
            starts.keys.toList().zipWithNext().filter { (l, r) -> r - l in 2..10 }.forEach { (l, r) ->
                for (x in l + 1 until r) starts[x] = (296 until 345).firstOrNull { solid(x, it) } ?: 345
            }
            val gap = starts.entries.sumOf { (x, start) ->
                (296 until start).count { y -> inside[y * body.width + x] && !solid(x, y) }
            }
            bitmap.recycle()
            bundle.assetKey to gap
        }.filterValues { it > 0 }
        body.recycle()
        assertEquals(emptyMap(), found, "상의 밑단과 하의 허리 사이 맨몸 띠([임시:D-성-185])")
    }

    @Test
    fun `14 하의 레이어에 위·아래가 천인 세로 4px 이하 틈이 없다`() {
        val found = bundles.filter { it.part == OutfitPart.BOTTOM }.flatMap { bundle ->
            genders.flatMap { gender ->
                slotsOf(OutfitPart.BOTTOM).map { bundle.file(gender, it, AvatarBodyShape.BASE) }
            }
        }.distinct().associateWith { path ->
            val bitmap = read(File(assets, path))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            StrayPixels.seams(pixels, bitmap.width, bitmap.height).also { bitmap.recycle() }
        }.filterValues { it > 0 }
        assertEquals(emptyMap(), found, "밑단 안감 사이 맨몸 점선([임시:D-성-185])")
    }

    @Test
    fun `16 옷 실루엣 안에 구멍(둘러싸인 반투명·투명 픽셀, 굵은 천 사이 좁은 트임)이 없다`() {
        val found = mutableMapOf<String, Int>()
        bundles.forEach { bundle ->
            genders.forEach { gender ->
                slotsOf(bundle.part).forEach { slot ->
                    val file = File(assets, bundle.file(gender, slot, AvatarBodyShape.BASE))
                    val template = templateOf(gender, slot, bundle)
                    val image = read(file)
                    // 남녀가 한 파일을 함께 쓸 수 있어 두 템플릿 허용 영역의 교집합 안만 센다(파이프라인 hole_area와 같다)
                    val allowed = genders.map {
                        read(File(masks, "${templateOf(it, slot, bundle)}_${bundle.part}.png"))
                    }
                    val pixels = IntArray(image.width * image.height)
                    image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
                    val holes = StrayPixels.holes(pixels, image.width, image.height)
                    // 달리기 다리의 엉덩이 잇기 칸(x 88~167, y 24~71)은 등 아래를 일부러 깎는다(파이프라인 hip.hip_box, 판정 round3 C)
                    val hip = slot == OutfitSlot.RUN_LEG
                    val count = holes.indices.count { i ->
                        val x = i % image.width
                        val y = i / image.width
                        val inHip = hip && x in 88 until 168 && y in 24 until 72
                        holes[i] && !inHip && allowed.all { on(it, x, y) }
                    }
                    if (count > 0) found["${file.parentFile!!.name}/${file.name} $template"] = count
                    image.recycle()
                    allowed.forEach { it.recycle() }
                }
            }
        }
        assertEquals(emptyMap(), found, "옷 안 구멍으로 맨몸·배경이 비친다(폴로 단추 자리)")
    }

    @Test
    fun `17 맨몸 바탕 외곽선은 정리 전 원본처럼 이어진다`() {
        // 기준: tools/outfit-pipeline/templates/masks/body_outline_<파일>.png(정리 전 승인 원본의 외곽선에서 고립 점·팔꿈치 꼬리·틈 조각을 뺀 것)
        val found = listOf("stand", "run_body", "run_arm", "run_leg").associateWith { slot ->
            val bitmap = read(File(outfits, "${OutfitCatalog.BODY}/female_$slot.webp"))
            val mask = read(File(masks, "body_outline_female_$slot.png"))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val reference = BooleanArray(pixels.size) { on(mask, it % bitmap.width, it / bitmap.width) }
            assertTrue(reference.count { it } > 500, "외곽선 기준 $slot")
            StrayPixels.outlineBreaks(pixels, reference).also {
                bitmap.recycle()
                mask.recycle()
            }
        }.filterValues { it > 0 }
        assertEquals(emptyMap(), found, "끊긴 맨몸 외곽선 픽셀(실기기에서 팔이 울퉁불퉁했다)")
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
