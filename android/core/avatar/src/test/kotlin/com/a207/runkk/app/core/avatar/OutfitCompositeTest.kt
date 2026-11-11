package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * 의상 레이어 합성(D-성-110·112, [임시:D-성-156] 상의 자르기, OUTFIT_SPEC.md 3절). 실제 자산(assets/outfits)으로 슬롯 텍스처를 만들어 픽셀을 본다.
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
                "outfits/shoes_carbon_racer/female_stand.webp",
                "outfits/bottom_leggings/female_stand.webp",
                "outfits/top_crew_tee/female_stand.webp"
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
            listOf("outfits/top_cherry_vest/female_run_arm.webp"),
            OutfitLayerComposer.layerFiles(
                catalog,
                OutfitSlot.RUN_ARM,
                AvatarGender.MALE,
                AvatarBodyShape.CHANGE_2,
                AvatarOutfit(top = 6),
                all
            ),
            "체형 전용 파일이 없으면 기준 파일, 남녀 같은 파일은 공용(shared)"
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
        assertEquals(listOf("outfits/base_bottom/female_run_leg.webp"), missing, "파일을 못 찾으면 base_*")
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
                // 소매 없는 상의의 달리기 팔은 비어 있다(파이프라인 spec.EMPTY_RUN_ARM_KEYS, 판정 round3)
                OutfitSlot.entries.filter { part in it.parts }
                    .filterNot { keys[id] == "top_lime_singlet" && it == OutfitSlot.RUN_ARM }
                    .forEach { slot ->
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

    @Test
    fun `상의 자르기 - 상의와 배경 사이 얇은 피부 띠는 지우고 상의에서 뻗은 팔과 칸 밖은 남긴다`() {
        // 맨몸: 가운데 몸통(x 4..15, y 2..19)과 팔(x 4..7, y 20..61, 상의에서 길게 뻗는다). 상의: x 6..13, y 4..19(맨몸보다 좌우 2px
        // 좁고 위로 2px 낮다).
        val body = png(20, 64) { bitmap ->
            for (y in 2..61) {
                for (x in 4..15) {
                    if (y < 20 || x <= 7) bitmap.setPixel(x, y, Color.rgb(250, 220, 200))
                }
            }
        }
        val top = png(20, 64) { bitmap -> for (y in 4..19) for (x in 6..13) bitmap.setPixel(x, y, Color.BLUE) }
        val area = png(20, 64) { bitmap -> for (y in 0..63) for (x in 0..14) bitmap.setPixel(x, y, Color.BLACK) }
        val out = decode(OutfitLayerComposer.compose(body, listOf(OutfitPart.TOP to top), 0, 20, 64, area))
        assertEquals(0, Color.alpha(out.getPixel(4, 10)), "상의 왼쪽 2px 띠(어깨·몸통 테두리)")
        assertEquals(0, Color.alpha(out.getPixel(9, 2)), "상의 위 2px 띠")
        assertEquals(Color.BLUE, out.getPixel(9, 10), "상의")
        assertEquals(255, Color.alpha(out.getPixel(5, 55)), "상의 아래로 뻗은 팔")
        assertEquals(255, Color.alpha(out.getPixel(6, 21)), "소매 끝 바로 아래 팔(상의는 위, 배경은 옆)")
        assertEquals(255, Color.alpha(out.getPixel(15, 10)), "자르기 칸 밖")
        out.recycle()
    }

    /**
     * 실기기 판정(남자 러닝량 100 싱글렛 옆 틈 흰 조각): Rive에 넣는 합성 텍스처에 거의 투명한데 색이 밝은 픽셀(미리곱으로 읽으면 밝게 뜬다)이
     * 없고, 옆 틈 칸(서기 x 80~159·352~431, y 150~309)의 상의 밖 맨몸 픽셀 가운데 톤을 받은 피부보다 훨씬 밝은 것이 없다. 상의 7벌 × 남녀 ×
     * 러닝량 0·50·100(밝기는 러닝량 100만).
     */
    @Test
    fun `서기 합성 텍스처에 거의 투명한 밝은 픽셀과 옆 틈의 톤 밖 밝은 맨몸이 없다(상의 7벌 × 남녀 × 러닝량 0·50·100)`() {
        val keys = OutfitCatalog.LEGACY_KEYS.getValue(OutfitPart.TOP)
        val slot = OutfitSlot.STAND
        val faint = mutableListOf<String>()
        val bright = mutableListOf<String>()
        for (gender in AvatarGender.entries) {
            for (id in keys.indices) {
                val outfit = AvatarOutfit(top = id)
                val top = decodeLayer(slot, gender, outfit, onlyTop = true)
                for (amount in listOf(0, 50, 100)) {
                    val composed = compose(slot, gender, outfit, amount)
                    val out = IntArray(slot.width * slot.height)
                    composed.getPixels(out, 0, slot.width, 0, 0, slot.width, slot.height)
                    composed.recycle()
                    val low = out.count {
                        val a = it ushr 24
                        a in 1 until OutfitLayerComposer.FAINT_ALPHA
                    }
                    if (low > 0) faint += "${keys[id]} $gender $amount: $low"
                    // 러닝량 100: 옆 틈 칸의 상의 밖 픽셀(불투명)은 톤을 받은 흰색(255 × 행렬) 밝기를 넘지 않는다(넘으면 톤 밖 픽셀)
                    if (amount != 100) continue
                    val m = SkinToneFilter.matrix(amount)
                    val ceiling =
                        luma(Color.rgb((255 * m[0]).toInt(), (255 * m[6]).toInt(), (255 * m[12]).toInt())) + 20
                    val over = (150 until 310).flatMap { y ->
                        ((80 until 160) + (352 until 432)).map { x -> y * slot.width + x }
                    }.count { top[it] ushr 24 == 0 && out[it] ushr 24 >= 128 && luma(out[it]) > ceiling }
                    if (over > 0) bright += "${keys[id]} $gender $amount: $over"
                }
            }
        }
        assertEquals(emptyList(), faint, "알파 1~15 픽셀")
        assertEquals(emptyList(), bright, "옆 틈의 톤 밖 밝은 맨몸")
    }

    @Test
    fun `남자 내장 기본 옷 몸 자리에 넣는 그림은 같은 크기의 완전 투명이다`() {
        val bitmap = decode(MaleSelectedBody.transparentPng)
        assertEquals(MaleSelectedBody.WIDTH to MaleSelectedBody.HEIGHT, bitmap.width to bitmap.height)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        assertTrue(pixels.all { it == 0 })
    }

    @Test
    fun `자르기 표지 - 상의 알파 1 칸의 맨몸만 지우고 표지는 옷으로 치지 않는다`() {
        // 맨몸: x 2..17, y 2..17. 상의: x 6..13, y 4..15. 표지: (3, 10) 한 칸(상의 밖, 띠 자르기 칸 밖이라 표지가 없으면 남는다)
        val body =
            png(20, 20) { bitmap -> for (y in 2..17) for (x in 2..17) bitmap.setPixel(x, y, Color.rgb(250, 220, 200)) }
        val top = png(20, 20) { bitmap ->
            for (y in 4..15) for (x in 6..13) bitmap.setPixel(x, y, Color.BLUE)
            bitmap.setPixel(3, 10, Color.argb(TopBodyCut.MARK_ALPHA, 0, 0, 0))
        }
        val area = png(20, 20) { }
        val out = decode(OutfitLayerComposer.compose(body, listOf(OutfitPart.TOP to top), 0, 20, 20, area))
        assertEquals(0, Color.alpha(out.getPixel(3, 10)), "표지 칸 맨몸은 지우고 표지도 그리지 않는다")
        assertEquals(255, Color.alpha(out.getPixel(3, 9)), "표지 옆 맨몸은 남는다")
        assertEquals(255, Color.alpha(out.getPixel(4, 10)), "자르기 칸 밖 맨몸은 남는다")
        assertEquals(Color.BLUE, out.getPixel(9, 10), "상의")
        out.recycle()
    }

    @Test
    fun `자르기 표지는 lime_singlet 서기에만 있고 그 칸의 맨몸은 서기 합성에서 지운다(판정 round6 밑단 옆 획)`() {
        val keys = OutfitCatalog.LEGACY_KEYS.getValue(OutfitPart.TOP)
        keys.forEachIndexed { id, key ->
            val top = pixels(File(root, "outfits/$key/female_stand.webp").readBytes())
            val marks = top.indices.filter { top[it] ushr 24 == TopBodyCut.MARK_ALPHA }
            if (key != "top_lime_singlet") {
                assertEquals(0, marks.size, "$key 에는 표지가 없다(crew·base 등은 그대로)")
                return@forEachIndexed
            }
            // BitmapFactory로 읽어도(앱 읽기와 같다) 표지가 그대로 남는다: 파이프라인 표지 칸 그림(PNG)의 흰 칸은 모두 표지이거나(맨몸 위)
            // 불투명 천이다(stand_fix가 표지 뒤 옆선을 이은 칸). 표지가 읽기에서 섞이거나 빠지면 알파 0이나 다른 값이 된다
            val template = javax.imageio.ImageIO.read(
                File("tools/outfit-pipeline/templates/marks/${key}__female_stand.png")
            )
            val width = template.width
            val white = (0 until template.height).flatMap { y ->
                (0 until width).filter { x -> template.raster.getSample(x, y, 0) > 0 }.map { x -> y * width + x }
            }
            assertTrue(marks.size > 700, "singlet 표지 ${marks.size}칸")
            assertTrue(marks.all { it in white.toSet() }, "표지는 표지 칸 그림 안에만")
            assertEquals(
                emptyList(),
                white.filter { top[it] ushr 24 != TopBodyCut.MARK_ALPHA && top[it] ushr 24 < 128 },
                "표지 칸 그림의 흰 칸이 읽은 뒤 표지도 천도 아니다"
            )
            val skin = pixels(File(root, "outfits/body/female_stand.webp").readBytes())
            assertTrue(marks.all { skin[it] ushr 24 != 0 }, "표지는 맨몸이 있는 칸에만")
            for (gender in AvatarGender.entries) {
                val composed = compose(OutfitSlot.STAND, gender, AvatarOutfit(top = id), 0)
                val out = IntArray(composed.width * composed.height)
                composed.getPixels(out, 0, composed.width, 0, 0, composed.width, composed.height)
                composed.recycle()
                val left = marks.count { out[it] ushr 24 != 0 }
                assertEquals(0, left, "$gender singlet 서기 합성에서 표지 칸에 맨몸도 표지도 남지 않는다")
            }
        }
    }

    @Test
    fun `상의 알파 밖 몸통 피부 테두리 0px - 21가지 × 남녀 × 서기·달리기 몸통·팔 × 러닝량 0·100`() {
        val slots = listOf(OutfitSlot.STAND, OutfitSlot.RUN_BODY, OutfitSlot.RUN_ARM)
        OutfitCatalog.LEGACY_KEYS.forEach { (part, keys) ->
            keys.indices.forEach { id ->
                val outfit = when (part) {
                    OutfitPart.TOP -> AvatarOutfit(top = id)
                    OutfitPart.BOTTOM -> AvatarOutfit(bottom = id)
                    OutfitPart.SHOES -> AvatarOutfit(shoes = id)
                }
                listOf(AvatarGender.FEMALE, AvatarGender.MALE).forEach { gender ->
                    slots.forEach { slot ->
                        listOf(0, 100).forEach { amount ->
                            assertNoRim(slot, gender, outfit, amount, "${keys[id]} $gender ${slot.fileName} $amount")
                        }
                    }
                }
            }
        }
    }

    /**
     * 자르기 칸 안에서 옷(상의·하의·신발) 밖(상의로 둘러싸인 빈 곳 제외)에 보이는 맨몸 피부를 4방향으로 이은 덩어리 가운데, 상의와 맞닿아 있고 상의에서
     * 가장 먼 픽셀까지 [TopBodyCut.RIM_PX] 이하인 것(옷 밖으로 비치는 어깨·몸통·소매 옆 피부 테두리)이 없다. 소매 끝 아래 팔·민소매 팔은
     * 상의에서 길게 뻗은 덩어리라, 목·손·하의 칸은 칸 밖이라 검사에 걸리지 않는다. 칸 밖의 보이는 맨몸(손·목)과 이어진 덩어리(손목)도 테두리가 아니다. [MIN_VISIBLE_RIM]픽셀 미만 조각(옷 가장자리
     * 안티에일리어싱, 외곽선 폭)은 보지 않는다.
     */
    private fun assertNoRim(
        slot: OutfitSlot,
        gender: AvatarGender,
        outfit: AvatarOutfit,
        amount: Int,
        message: String
    ) {
        val width = slot.width
        val height = slot.height
        val size = width * height
        val composed = compose(slot, gender, outfit, amount)
        val pixels = IntArray(size).also { composed.getPixels(it, 0, width, 0, 0, width, height) }
        composed.recycle()
        val top = decodeLayer(slot, gender, outfit, onlyTop = true)
        val clothes = decodeLayer(slot, gender, outfit)
        val areaBitmap = decode(OutfitLibrary.read(checkNotNull(catalog.cutFile(gender, slot)))!!)
        val area = IntArray(size).also { areaBitmap.getPixels(it, 0, width, 0, 0, width, height) }
        areaBitmap.recycle()
        val outside = TopBodyCut.outsideOf(top, width, height)
        // 기본 의상 서기는 승인 기본 텍스처 그대로가 정답이다: 맨몸이 승인 픽셀과 같은 곳(팔과 밑단이 만나는 승인 그림)은 테두리가 아니다.
        val approved = if (outfit.isDefault) approvedMask(slot, gender) else null
        val visible = uvMask(slot, gender, outfit)
        // 맨몸 외곽선(러닝량 0에서 어두운 갈색 선)은 피부 테두리가 아니다: 옷 외곽선과 같은 색이라 옷 가장자리 선으로 보인다.
        val light = compose(slot, gender, outfit, 0)
        val outline = BooleanArray(size) { luma(light.getPixel(it % width, it / width)) < 110 }
        light.recycle()
        val exposed = BooleanArray(size) {
            clothes[it] ushr 24 == 0 && pixels[it] ushr 24 != 0 && area[it] ushr 24 >= 128 && outside[it] &&
                visible[it] && (approved == null || !approved[it])
        }
        val distance = IntArray(size) { Int.MAX_VALUE }
        val queue = ArrayDeque<Int>()
        for (i in 0 until size) {
            if (exposed[i] && neighbors(i, width, height).any { top[it] ushr 24 != 0 }) {
                distance[i] = 1
                queue.add(i)
            }
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            neighbors(i, width, height).filter { exposed[it] && distance[it] == Int.MAX_VALUE }.forEach {
                distance[it] = distance[i] + 1
                queue.add(it)
            }
        }
        val seen = BooleanArray(size)
        val rims = mutableListOf<String>()
        for (start in 0 until size) {
            if (!exposed[start] || seen[start]) continue
            val members = mutableListOf(start)
            seen[start] = true
            var k = 0
            while (k < members.size) {
                neighbors(members[k++], width, height).filter { exposed[it] && !seen[it] }.forEach {
                    seen[it] = true
                    members += it
                }
            }
            val farthest = members.maxOf { distance[it] }
            val skin = members.count { !outline[it] }
            // 칸 밖의 보이는 맨몸(손·목)과 이어진 덩어리는 손목·목처럼 몸이 이어지는 곳이라 테두리가 아니다(DV3-8: 소매 끝 손목 피부를
            // 옷 레이어에서 지우고 맨몸 바탕이 그리게 했다)
            val joinsUncut = members.any { m ->
                neighbors(m, width, height).any { n ->
                    area[n] ushr 24 < 128 && clothes[n] ushr 24 == 0 && pixels[n] ushr 24 != 0
                }
            }
            if (farthest <= TopBodyCut.RIM_PX && skin >= MIN_VISIBLE_RIM && !joinsUncut) {
                rims += "$message (${members[0] % width}, ${members[0] / width}) ${skin}px"
            }
        }
        assertTrue(rims.isEmpty(), "피부 테두리 ${rims.size}곳: ${rims.take(5)}")
    }

    /** riv 메시 UV가 덮어 화면에 보이는 픽셀(tools/outfit-pipeline/templates/masks, 템플릿 이름은 OUTFIT_SPEC.md 4절). */
    private fun uvMask(slot: OutfitSlot, gender: AvatarGender, outfit: AvatarOutfit): BooleanArray {
        val g = gender.fileName
        val name = when {
            slot != OutfitSlot.STAND -> "${g}_${slot.fileName}"
            gender == AvatarGender.FEMALE -> "female_stand"
            else -> "male_stand_s${catalog.bundle(OutfitPart.TOP, outfit.top)!!.maleTopSurface}"
        }
        val mask = BitmapFactory.decodeFile("tools/outfit-pipeline/templates/masks/$name.png")
        val width = mask.width
        return BooleanArray(width * mask.height) { mask.getPixel(it % width, it / width) and 0xFFFFFF != 0 }
            .also { mask.recycle() }
    }

    /** 기본 의상 러닝량 0 합성이 승인 기본 텍스처(서기 PNG, 달리기는 riv 내장)와 같은 픽셀(러닝량과 무관하게 같은 자리). */
    @Test
    fun `기본 의상 서기 합성은 승인 기본 텍스처의 몸 실루엣 안(팔·몸통, 소매 아래)에 투명 구멍이 없다`() {
        val approved = pixels(approvedBytes(OutfitSlot.STAND))
        val width = OutfitSlot.STAND.width
        val found = mutableListOf<String>()
        AvatarGender.entries.forEach { gender ->
            val visible = uvMask(OutfitSlot.STAND, gender, AvatarOutfit())
            val composed = compose(OutfitSlot.STAND, gender, AvatarOutfit(), 100)
            // 소매 아래(y 185)부터 손 위(y 300)까지: 팔 띠·몸통 옆·허리 위. 소매 끝 모서리(y 185 위)와 목은 상의 자르기·머리가 정한다.
            for (y in 185..300) {
                for (x in 0 until width) {
                    val i = y * width + x
                    if (visible[i] && approved[i] ushr 24 >= 250 &&
                        composed.getPixel(x, y) ushr 24 < 128
                    ) {
                        found += "$gender ($x, $y)"
                    }
                }
            }
            composed.recycle()
        }
        assertEquals(emptyList(), found.take(10), "몸 실루엣 안 투명 픽셀 ${found.size}")
    }

    @Test
    fun `짧은 상의 × 모든 하의 서기 합성에서 밑단과 허리 사이 몸통에 투명 구멍이 없다`() {
        val approved = pixels(approvedBytes(OutfitSlot.STAND))
        val found = mutableListOf<String>()
        // 짧은 상의(반팔·민소매): 기본, 라임 싱글렛, 크루 티
        listOf(0, 1, 2).forEach { top ->
            OutfitCatalog.LEGACY_KEYS.getValue(OutfitPart.BOTTOM).indices.forEach { bottom ->
                AvatarGender.entries.forEach { gender ->
                    val outfit = AvatarOutfit(top = top, bottom = bottom)
                    val composed = compose(OutfitSlot.STAND, gender, outfit, 100)
                    val width = OutfitSlot.STAND.width
                    var holes = 0
                    // 몸통 열(x 128~383)의 허리 밴드(y 296~321, 상의 밑단과 하의 허리 사이), 승인 기본 텍스처가 불투명한 곳
                    for (y in 296..321) {
                        for (x in 128..383) {
                            if (approved[y * width + x] ushr 24 >= 250 && composed.getPixel(x, y) ushr 24 < 128) holes++
                        }
                    }
                    composed.recycle()
                    if (holes > 0) found += "top $top bottom $bottom $gender: $holes"
                }
            }
        }
        assertEquals(emptyList(), found, "밑단과 허리 사이 투명 픽셀")
    }

    @Test
    fun `모든 상의 × 하의 × 남녀 서기 합성에서 밑단과 허리 사이와 하의 둘레에 맨몸 속옷·짙은 띠가 비치지 않는다`() {
        val found = mutableListOf<String>()
        val width = OutfitSlot.STAND.width
        val (xs, ys) = StrayPixels.HIPS
        OutfitCatalog.LEGACY_KEYS.getValue(OutfitPart.TOP).indices.forEach { top ->
            OutfitCatalog.LEGACY_KEYS.getValue(OutfitPart.BOTTOM).indices.forEach { bottom ->
                AvatarGender.entries.forEach { gender ->
                    val outfit = AvatarOutfit(top = top, bottom = bottom)
                    val clothes = decodeLayer(OutfitSlot.STAND, gender, outfit)
                    val plain = compose(OutfitSlot.STAND, gender, outfit, 0)
                    val toned = compose(OutfitSlot.STAND, gender, outfit, 100)
                    var neutral = 0
                    var dark = 0
                    for (y in ys) {
                        for (x in xs) {
                            if (clothes[y * width + x] ushr 24 != 0) continue
                            val c = plain.getPixel(x, y)
                            if (Color.alpha(c) < 128) continue
                            val channels = listOf(Color.red(c), Color.green(c), Color.blue(c))
                            // 러닝량 0에서 옷 밖으로 보이는 맨몸의 회색(속옷). 몸 외곽선(밝기 60 미만)은 빼고 센다
                            if (channels.max() - channels.min() < 25 && luma(c) >= 60) neutral++
                            // 몸통 열(x 128~383)의 허리 밴드: 러닝량 100 맨몸 피부는 밝기 80 이상, 톤 걸린 속옷 허리 밴드는 40 아래였다
                            if (x in 128..383 && y < 322 && luma(toned.getPixel(x, y)) < 60) dark++
                        }
                    }
                    plain.recycle()
                    toned.recycle()
                    if (neutral + dark > 0) found += "top $top bottom $bottom $gender: 무채색 $neutral, 짙음 $dark"
                }
            }
        }
        assertEquals(emptyList(), found, "[임시:D-성-185]")
    }

    @Test
    fun `하의 7벌 × 남녀 × 서기·달리기 다리에서 하의 안 맨몸 실루엣이 비지 않는다`() {
        val found = mutableListOf<String>()
        OutfitCatalog.LEGACY_KEYS.getValue(OutfitPart.BOTTOM).indices.forEach { bottom ->
            AvatarGender.entries.forEach { gender ->
                // 서기: 허리·엉덩이 칸(y 296~411)에서 하의가 그 행에 걸친 가로 범위 안. 범위 밖(옛 반바지 실루엣 테두리)은 자르기가 지운다
                OutfitCatalog.LEGACY_KEYS.getValue(OutfitPart.TOP).indices.forEach { top ->
                    val outfit = AvatarOutfit(top = top, bottom = bottom)
                    val holes = uncovered(OutfitSlot.STAND, gender, outfit, 296 until 412, insideBottomSpan = true)
                    if (holes > 0) found += "stand top $top bottom $bottom $gender: $holes"
                }
                // 달리기 다리: 슬롯 전체(자르기가 없다)
                val outfit = AvatarOutfit(bottom = bottom)
                val holes = uncovered(OutfitSlot.RUN_LEG, gender, outfit, 0 until OutfitSlot.RUN_LEG.height, false)
                if (holes > 0) found += "run_leg bottom $bottom $gender: $holes"
            }
        }
        assertEquals(emptyList(), found, "맨몸 실루엣 안인데 아무 레이어도 덮지 않은 픽셀([임시:D-성-185])")
    }

    /** 맨몸 바탕이 불투명한데 합성 결과가 비는 픽셀 수([rows] 행, [insideBottomSpan]이면 그 행 하의의 맨 왼쪽~맨 오른쪽 사이만). */
    private fun uncovered(
        slot: OutfitSlot,
        gender: AvatarGender,
        outfit: AvatarOutfit,
        rows: IntRange,
        insideBottomSpan: Boolean
    ): Int {
        val width = slot.width
        val body = pixels(OutfitLibrary.read(checkNotNull(catalog.bodyFile(gender, slot)))!!)
        val bottom = layersOf(slot, gender, outfit).first { it.first == OutfitPart.BOTTOM }.second.let(::pixels)
        val composed = compose(slot, gender, outfit, 0)
        var holes = 0
        for (y in rows) {
            val span = if (insideBottomSpan) {
                val xs = (0 until width).filter { bottom[y * width + it] ushr 24 >= 128 }
                if (xs.isEmpty()) continue
                xs.first()..xs.last()
            } else {
                0 until width
            }
            for (x in span) {
                if (body[y * width + x] ushr 24 >= 128 && Color.alpha(composed.getPixel(x, y)) < 128) holes++
            }
        }
        composed.recycle()
        return holes
    }

    @Test
    fun `민소매 서기 합성의 맨팔에 소매 밑단 가로 줄이 없다(남녀 × 체형 × 러닝량 0·100)`() {
        val lines = mutableListOf<String>()
        AvatarGender.entries.forEach { gender ->
            AvatarBodyShape.entries.forEach { shape ->
                listOf(0, 100).forEach { amount ->
                    val composed = compose(OutfitSlot.STAND, gender, AvatarOutfit(top = 1), amount, shape = shape)
                    lines += sleeveLines(composed).map { "$gender $shape $amount $it" }
                    composed.recycle()
                }
            }
        }
        assertEquals(emptyList(), lines.take(10), "가로 줄 ${lines.size}([임시:D-성-184])")
    }

    /**
     * 팔 안쪽(불투명이 7x7로 이어지고 투명한 곳에서 5px 넘게 떨어진 곳, 소매 높이 y 140~205)의 가로 줄: 바로 아래 행과 밝기가 8 넘게 뛰는 밝은
     * 피부 줄, 그리고 위·아래 3행 평균보다 14 넘게 어두운 줄(맨몸 외곽선은 투명한 곳에 붙어 있어 빠진다).
     */
    private fun sleeveLines(composed: Bitmap): List<String> {
        val found = mutableListOf<String>()
        fun opaque(px: Int, py: Int) = Color.alpha(composed.getPixel(px, py)) >= 250
        fun l(px: Int, py: Int) = luma(composed.getPixel(px, py))
        for (x in (70..125) + (386..441)) {
            for (y in 140 until 205) {
                val inside = (-5..5).all { dy -> (-5..5).all { dx -> opaque(x + dx, y + dy) } }
                if (!inside) continue
                val bright = l(x, y) >= 150 && l(x, y + 1) >= 150 && abs(l(x, y) - l(x, y + 1)) > 8
                val dark = l(x, y) < (l(x, y - 3) + l(x, y + 3)) / 2 - 14
                if (bright || dark) found += "($x, $y)"
            }
        }
        return found
    }

    @Test
    fun `기본 의상 러닝량 0 서기 합성의 팔 가장자리는 승인 기본 텍스처와 알파·색이 같다`() {
        val approved = pixels(approvedBytes(OutfitSlot.STAND))
        val composed = compose(OutfitSlot.STAND, AvatarGender.FEMALE, AvatarOutfit(), 0)
        val width = OutfitSlot.STAND.width
        val opaque = BooleanArray(approved.size) { approved[it] ushr 24 >= 128 }
        var edge = 0
        val differs = mutableListOf<String>()
        // 팔 띠(소매 밑 y 190~300, 바깥 열 x < 140·x > 372)에서 승인 실루엣 경계 2px 안. y 296부터 허리 쪽 열(x 110~139·373~401)은 허리 밴드
        // 자리라 건너뛴다(기본 하의가 그린다, [임시:D-성-185]. 허리 옆 계단을 원래 허리 가장자리로 이어 x 110·401까지 하의가 덮는다, 판정 round1 A)
        for (y in 190..300) {
            for (x in (0 until 140) + (373 until width)) {
                if (y >= 296 && (x in 110..139 || x in 373..401)) continue
                val i = y * width + x
                val near = (-2..2).any { dy ->
                    (-2..2).any { dx ->
                        opaque[
                            (y + dy) * width +
                                (x + dx).coerceIn(0, width - 1)
                        ] !=
                            opaque[i]
                    }
                }
                if (!near) continue
                edge++
                val a = approved[i]
                val c = composed.getPixel(x, y)
                val alpha = abs((a ushr 24) - (c ushr 24))
                val color = if (a ushr 24 >=
                    128
                ) {
                    listOf(16, 8, 0).sumOf { abs((a shr it and 0xFF) - (c shr it and 0xFF)) }
                } else {
                    0
                }
                if (alpha > 24 || color > 30) differs += "($x, $y)"
            }
        }
        composed.recycle()
        assertTrue(edge > 300, "검사한 가장자리 $edge")
        assertEquals(emptyList(), differs.take(10), "팔 가장자리 차이 ${differs.size}/$edge")
    }

    private fun approvedBytes(slot: OutfitSlot): ByteArray = if (slot == OutfitSlot.STAND) {
        File("src/main/res/raw/avatar_legacy_standing_body.png").readBytes()
    } else {
        val asset = mapOf(
            OutfitSlot.RUN_BODY to "side_body_coral",
            OutfitSlot.RUN_ARM to "continuous_arm",
            OutfitSlot.RUN_LEG to "side_leg_skin",
            OutfitSlot.RUN_SHOE to "side_shoe"
        ).getValue(slot)
        RivEmbeddedImages.read(File("src/main/res/raw/avatar_run_approved.riv").readBytes()).getValue(asset)
    }

    private fun approvedMask(slot: OutfitSlot, gender: AvatarGender): BooleanArray {
        val bytes = if (slot == OutfitSlot.STAND) {
            File("src/main/res/raw/avatar_legacy_standing_body.png").readBytes()
        } else {
            val asset = mapOf(
                OutfitSlot.RUN_BODY to "side_body_coral",
                OutfitSlot.RUN_ARM to "continuous_arm",
                OutfitSlot.RUN_LEG to "side_leg_skin",
                OutfitSlot.RUN_SHOE to "side_shoe"
            ).getValue(slot)
            RivEmbeddedImages.read(File("src/main/res/raw/avatar_run_approved.riv").readBytes()).getValue(asset)
        }
        val legacy = decode(bytes)
        val composed = compose(slot, gender, AvatarOutfit(), 0)
        val width = legacy.width
        return BooleanArray(width * legacy.height) {
            val a = legacy.getPixel(it % width, it / width)
            val c = composed.getPixel(it % width, it / width)
            // 서기 팔의 소매 밑단 행(y 150~190, 팔 열 x < 145·x >= 367)은 소매 줄을 펴서 승인 색과 조금 다르다([임시:D-성-184]): 그곳만 승인
            // 그림에서도 팔(불투명)이 있던 곳이면 팔이다. 나머지는 승인 픽셀과 같아야 한다
            val x = it % width
            val armSeam = slot == OutfitSlot.STAND && it / width in 150..190 && (x < 145 || x >= 367)
            near(a, c, 6) || (armSeam && Color.alpha(a) >= 128 && Color.alpha(c) >= 250)
        }.also {
            legacy.recycle()
            composed.recycle()
        }
    }

    private fun neighbors(i: Int, width: Int, height: Int): List<Int> = buildList {
        val x = i % width
        if (x > 0) add(i - 1)
        if (x < width - 1) add(i + 1)
        if (i >= width) add(i - width)
        if (i < (height - 1) * width) add(i + width)
    }

    private fun png(width: Int, height: Int, draw: (Bitmap) -> Unit): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(bitmap)
        return AvatarImages.encode(bitmap).also { bitmap.recycle() }
    }

    private fun decode(bytes: ByteArray): Bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

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

    @Test
    fun `달리기 몸통 세 자리는 같은 모습이면 한 번만 합성해 같은 결과를 나눠 쓴다`() {
        val names = listOf("side_body_coral", "side_body_cream", "side_body_blue")
        val request = AvatarToneRequest(
            version = 1,
            identity = 1,
            gender = AvatarGender.MALE,
            amount = 60,
            outfit = AvatarOutfit(top = 4),
            shape = AvatarBodyShape.BASE,
            face = null,
            hair = null,
            frontFullness = null,
            runFullness = null,
            sideLayers = null,
            skinAssets = names.associateWith { byteArrayOf() },
            generatedSkinColor = null
        )
        val prepared = AvatarTextureComposer.compose(request, AvatarToneCache())
        val first = prepared.skinRenders.getValue(names.first())
        names.forEach { assertSame(first, prepared.skinRenders.getValue(it), it) }
        names.forEach { assertSame(prepared.skinBases.getValue(names.first()), prepared.skinBases.getValue(it), it) }
    }

    @Test
    fun `깨진 레이어는 그 부위 base_* 로 그리고 그것도 없으면 건너뛴다(전체 실패 없음)`() {
        val slot = OutfitSlot.STAND
        val gender = AvatarGender.FEMALE
        val outfit = AvatarOutfit(top = 2, bottom = 2, shoes = 3)
        val layers = layersOf(slot, gender, outfit)
        val broken = layers.map { (part, bytes) -> part to if (part == OutfitPart.TOP) byteArrayOf(1, 2, 3) else bytes }
        val baseTop = OutfitLibrary.read(
            catalog.bundle(
                OutfitPart.TOP,
                OutfitCatalog.baseKey(OutfitPart.TOP)
            )!!.file(gender, slot, AvatarBodyShape.BASE)
        )!!
        val expected = layers.map { (part, bytes) -> part to if (part == OutfitPart.TOP) baseTop else bytes }

        val fallback = pixels(
            OutfitLayerComposer.compose(null, broken, 0, slot.width, slot.height) { part ->
                if (part == OutfitPart.TOP) baseTop else null
            }
        )
        assertContentEquals(pixels(OutfitLayerComposer.compose(null, expected, 0, slot.width, slot.height)), fallback)

        val skipped = pixels(OutfitLayerComposer.compose(null, broken, 0, slot.width, slot.height))
        val withoutTop = layers.filter { it.first != OutfitPart.TOP }
        assertContentEquals(pixels(OutfitLayerComposer.compose(null, withoutTop, 0, slot.width, slot.height)), skipped)
    }

    @Test
    fun `러닝량 100 합성에는 톤이 안 먹은 밝은 원래 피부가 없다(21가지 × 남녀 × 슬롯)`() {
        val found = mutableListOf<String>()
        OutfitCatalog.LEGACY_KEYS.forEach { (part, keys) ->
            keys.indices.forEach { id ->
                val outfit = when (part) {
                    OutfitPart.TOP -> AvatarOutfit(top = id)
                    OutfitPart.BOTTOM -> AvatarOutfit(bottom = id)
                    OutfitPart.SHOES -> AvatarOutfit(shoes = id)
                }
                OutfitSlot.entries.filter { part in it.parts }.forEach { slot ->
                    AvatarGender.entries.forEach { gender ->
                        val count = untonedSkin(slot, gender, outfit)
                        if (count > 0) found += "${keys[id]} $gender $slot: $count"
                    }
                }
            }
        }
        assertEquals(emptyList(), found, "옷 레이어에서 온 밝은 맨살(빈 곳 = 옷이 없는 곳)")
    }

    /** 러닝량 100 합성에서 옷이 없는 곳(맨몸만 보이는 곳)에 닿은 맨살 픽셀 수([ExposedSkin]). 톤 걸린 피부는 맨살 색조가 아니다. */
    private fun untonedSkin(slot: OutfitSlot, gender: AvatarGender, outfit: AvatarOutfit): Int {
        val size = slot.width * slot.height
        val clothed = BooleanArray(size)
        layersOf(slot, gender, outfit).forEach { (_, bytes) ->
            val layer = pixels(bytes)
            for (i in 0 until size) if (layer[i] ushr 24 >= 32) clothed[i] = true
        }
        val composite = compose(slot, gender, outfit, 100)
        val result = IntArray(size).also { composite.getPixels(it, 0, slot.width, 0, 0, slot.width, slot.height) }
        composite.recycle()
        return ExposedSkin.count(result, slot.width, slot.height) { !clothed[it] }
    }

    private fun pixels(bytes: ByteArray): IntArray {
        val bitmap = decode(bytes)
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
        }
    }

    private fun layersOf(
        slot: OutfitSlot,
        gender: AvatarGender,
        outfit: AvatarOutfit,
        shape: AvatarBodyShape = AvatarBodyShape.BASE
    ) = OutfitLayerComposer.layers(catalog, slot, gender, shape, outfit) {
        OutfitLibrary.read(it) != null
    }
        .map { it.part to OutfitLibrary.read(it.path)!! }

    /** 그 모습의 의상 레이어만 겹친 픽셀(맨몸 없이). [onlyTop]이면 상의만. */
    private fun decodeLayer(
        slot: OutfitSlot,
        gender: AvatarGender,
        outfit: AvatarOutfit,
        onlyTop: Boolean = false
    ): IntArray {
        val layers = layersOf(slot, gender, outfit).filter { !onlyTop || it.first == OutfitPart.TOP }
        val bytes = OutfitLayerComposer.compose(null, layers, 0, slot.width, slot.height)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        return IntArray(slot.width * slot.height).also {
            bitmap.getPixels(it, 0, slot.width, 0, 0, slot.width, slot.height)
            bitmap.recycle()
        }
    }

    private fun compose(
        slot: OutfitSlot,
        gender: AvatarGender,
        outfit: AvatarOutfit,
        amount: Int,
        onlyTop: Boolean = false,
        shape: AvatarBodyShape = AvatarBodyShape.BASE
    ): Bitmap {
        val body = catalog.bodyFile(gender, slot)?.let { OutfitLibrary.read(it) }
        val layers = layersOf(slot, gender, outfit, shape).filter { !onlyTop || it.first == OutfitPart.TOP }
        val cut = catalog.cutFile(gender, slot)?.let { OutfitLibrary.read(it) }
        val bytes = OutfitLayerComposer.compose(body, layers, amount, slot.width, slot.height, cut)
        return decode(bytes)
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

    private companion object {
        /** 눈에 띄는 피부 테두리 조각의 최소 크기(픽셀 수, 4x4). 이보다 작은 조각은 옷 모서리 안티에일리어싱 수준이다. */
        const val MIN_VISIBLE_RIM = 16
    }
}
