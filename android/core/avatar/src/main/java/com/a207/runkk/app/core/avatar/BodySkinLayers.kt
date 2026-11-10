package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff

/**
 * 승인된 몸 텍스처를 피부 바탕과 옷 덮개로 나눠 두고(캐시), 러닝량 톤은 피부에만 적용한 뒤 옷을 원래 색으로 얹는다(PoC BodySkinLayers).
 * 한 픽셀은 피부와 옷 가운데 한쪽에만 속한다(알파를 나누면 가장자리 덮임이 사라진다).
 */
internal object BodySkinLayers {
    const val STANDING_BODY = "standing_body"

    /** 기본 의상(흰 반팔티, 검은 반바지)의 서기 몸. res/raw/avatar_legacy_standing_body.png. */
    const val STANDING_BODY_LEGACY = "standing_body_legacy"

    private const val CORAL = 6

    /** 원본 텍스처와 피부·옷 분리 결과. */
    data class PreparedLayers(
        val original: ByteArray,
        val skin: ByteArray,
        val clothing: ByteArray,
        val skinPixels: Int,
        val assetName: String = ""
    )

    fun prepare(assetName: String, bytes: ByteArray): PreparedLayers {
        val (width, height) = dimensions(assetName)
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("unreadable body texture: $assetName")
        try {
            require(source.width == width && source.height == height) { "body texture size differs: $assetName" }
            val clothing = IntArray(source.width * source.height)
            source.getPixels(clothing, 0, source.width, 0, 0, source.width, source.height)
            val skin = IntArray(clothing.size)
            var skinPixels = 0
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    val index = y * source.width + x
                    val pixel = clothing[index]
                    if (isSkin(assetName, x, y, pixel)) {
                        skin[index] = pixel
                        clothing[index] = Color.TRANSPARENT
                        skinPixels++
                    }
                }
            }
            check(skinPixels > 0 || assetName == "side_shoe") { "no skin in $assetName" }
            // 큰 원본에서 전체 해상도 Bitmap 둘이 동시에 살지 않도록 한 쪽씩 인코딩한다.
            val skinBytes = encodePixels(skin, source.width, source.height)
            val clothingBytes = encodePixels(clothing, source.width, source.height)
            return PreparedLayers(
                bytes,
                skinBytes,
                clothingBytes,
                skinPixels,
                if (assetName == STANDING_BODY_LEGACY) STANDING_BODY else assetName
            )
        } finally {
            source.recycle()
        }
    }

    /** 흰 티·검은 반바지가 기본이고, 다듬은 코랄 의상은 부위마다 6번으로 고른다. 남자는 자기 의상만 쓴다. */
    fun renderStanding(
        polished: PreparedLayers,
        legacy: PreparedLayers,
        amount: Int,
        selection: AvatarOutfit,
        maleOutfit: Boolean = false
    ): ByteArray {
        // 남자 의상은 6번을 포함해 따로 재단한다. 여자 코랄 의상 그림을 남자 의상 아틀라스에 붙이지 않는다.
        if (maleOutfit) return render(legacy, amount, selection, maleOutfit = true)
        if (selection.top != CORAL && selection.bottom != CORAL && selection.shoes != CORAL) {
            return render(legacy, amount, selection)
        }
        if (selection.top == CORAL && selection.bottom == CORAL && selection.shoes == CORAL) {
            return render(polished, amount)
        }
        val normalSelection = AvatarOutfit(
            if (selection.top == CORAL) 0 else selection.top,
            if (selection.bottom == CORAL) 0 else selection.bottom,
            if (selection.shoes == CORAL) 0 else selection.shoes
        )
        val regions = coralRegions(selection)
        val skin = mergeLayer(legacy.skin, polished.skin, regions)
        val clothing = mergeLayer(legacy.clothing, polished.clothing, regions)
        val mixed =
            PreparedLayers(legacy.original, skin, clothing, legacy.skinPixels + polished.skinPixels, STANDING_BODY)
        // 러닝량 0이어도 합성한다. mixed.original은 바뀌지 않은 원본이라 고른 부위를 건너뛰면 안 된다.
        return render(mixed, amount, normalSelection, composeOriginal = false)
    }

    fun render(
        layers: PreparedLayers,
        amount: Int,
        selection: AvatarOutfit = AvatarOutfit(),
        composeOriginal: Boolean = true,
        maleOutfit: Boolean = false
    ): ByteArray {
        val matrix = SkinToneFilter.matrix(amount)
        val referenceBody = layers.assetName == MaleSelectedBody.ASSET
        if (composeOriginal && amount == MIN_RUNNING_AMOUNT && (referenceBody || selection.isDefault)) {
            return layers.original
        }
        val skin = decodeMutable(layers.skin)
        try {
            val clothing = decodeMutable(layers.clothing)
            try {
                // 남자 선택 몸의 옷은 Rive 메시가 직접 보여 준다. 그 밖의 옷은 512×768 아틀라스 메시에 그린다.
                if (!referenceBody) {
                    if (maleOutfit) {
                        MaleOutfitStyles.apply(layers.assetName, skin, clothing, selection)
                    } else {
                        OutfitStyles.apply(layers.assetName, skin, clothing, selection)
                    }
                }
                val result = Bitmap.createBitmap(skin.width, skin.height, Bitmap.Config.ARGB_8888)
                try {
                    Canvas(result).apply {
                        drawBitmap(skin, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
                        drawBitmap(clothing, 0f, 0f, null)
                    }
                    return AvatarImages.encode(result)
                } finally {
                    result.recycle()
                }
            } finally {
                clothing.recycle()
            }
        } finally {
            skin.recycle()
        }
    }

    private fun dimensions(assetName: String): Pair<Int, Int> = when (assetName) {
        MaleSelectedBody.ASSET -> MaleSelectedBody.WIDTH to MaleSelectedBody.HEIGHT
        STANDING_BODY, STANDING_BODY_LEGACY -> 512 to 768
        "continuous_arm" -> 256 to 1024
        "side_leg_skin" -> 256 to 768
        "side_body_coral", "side_body_cream", "side_body_blue" -> 256 to 384
        "side_head" -> 1024 to 1024
        "side_shoe" -> 256 to 160
        else -> throw IllegalArgumentException("unsupported skin asset: $assetName")
    }

    private fun coralRegions(selection: AvatarOutfit) = Path().apply {
        if (selection.top == CORAL) {
            addRect(0f, 0f, 512f, 320f, Path.Direction.CW)
            addRect(0f, 300f, 115f, 410f, Path.Direction.CW)
            addRect(400f, 300f, 512f, 410f, Path.Direction.CW)
        }
        if (selection.bottom == CORAL) addRect(95f, 318f, 418f, 426f, Path.Direction.CW)
        if (selection.shoes == CORAL) addRect(80f, 622f, 430f, 768f, Path.Direction.CW)
    }

    /** 기존 옷을 그리기 전에 그림 부위를 고른다. 그래서 긴 바지는 신발 위에 남고 부위 경계에서 잘리지 않는다. */
    private fun mergeLayer(original: ByteArray, replacement: ByteArray, regions: Path): ByteArray {
        val target = decodeMutable(original)
        try {
            val source = BitmapFactory.decodeByteArray(replacement, 0, replacement.size)
                ?: throw IllegalArgumentException("unreadable coral outfit layer")
            try {
                require(
                    target.width == 512 && target.height == 768 &&
                        source.width == target.width && source.height == target.height
                ) { "standing body layers differ in size" }
                Canvas(target).apply {
                    save()
                    clipPath(regions)
                    drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    drawBitmap(source, 0f, 0f, null)
                    restore()
                }
                return AvatarImages.encode(target)
            } finally {
                source.recycle()
            }
        } finally {
            target.recycle()
        }
    }

    private fun isSkin(name: String, x: Int, y: Int, pixel: Int): Boolean {
        if (name == MaleSelectedBody.ASSET) return MaleSelectedBody.isSkin(x, y, pixel)
        if (Color.alpha(pixel) == 0) return false
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        // 옆이마 가장자리의 거의 흰 피부는 복숭아빛이 없다. 눈썹·눈 위로만 예외를 둬 흰 옷과 떨어뜨린다.
        val brightForehead = name == "side_head" && inPolygon(x, y, foreheadCoverage) &&
            r >= 230 && g >= 210 && b >= 185 && r >= g - 3 && g >= b - 3
        val peachSkin = r >= 175 && g >= 95 && b >= 65 && r - g >= 6 && g - b >= 3
        if (!peachSkin && !brightForehead) return false
        return when (name) {
            STANDING_BODY ->
                inPolygon(x, y, polishedNeck) ||
                    inPolygon(x, y, polishedHand) ||
                    inPolygon(512 - x, y, polishedHand) ||
                    (y in 405..622 && x in 110..400)

            STANDING_BODY_LEGACY ->
                (
                    y in 156..400 && (
                        (x <= 150 && belowSleeve(x, y, 145 + x * .27, legacyLeftHem)) ||
                            (x >= 365 && belowSleeve(x, y, 145 + (512 - x) * .27, legacyRightHem))
                        )
                    ) ||
                    (y in 405..632 && x in 110..400)

            "continuous_arm" -> y >= 330 || (x >= 50 && y >= 260 + x * .25)

            "side_leg_skin" -> y in 267..697 || y in 0..65

            "side_body_coral", "side_body_cream", "side_body_blue" -> inPolygon(x, y, neckOutline)

            "side_head" -> inPolygon(x, y, faceOutline) && !inPolygon(x, y, eyeOutline)

            else -> false
        }
    }

    private val polishedNeck = intArrayOf(
        218, 0, 298, 0, 296, 17, 302, 23, 279, 32, 256, 34, 232, 30, 215, 24, 220, 16
    )
    private val polishedHand = intArrayOf(
        28, 299, 48, 299, 68, 306, 89, 316, 98, 348, 82, 400, 0, 400, 0, 330, 14, 310
    )
    private val faceOutline = intArrayOf(
        750, 240, 815, 250, 840, 545, 860, 590, 825, 685, 760, 715,
        650, 752, 610, 690, 475, 625, 470, 540, 620, 500, 700, 420
    )
    private val eyeOutline = intArrayOf(
        723, 485, 748, 476, 766, 481, 775, 500,
        774, 541, 763, 563, 746, 570, 726, 559, 719, 529
    )
    private val foreheadCoverage = intArrayOf(
        764, 250, 792, 260, 817, 320, 828, 380, 824, 435,
        695, 435, 725, 395, 745, 350, 757, 305
    )
    private val neckOutline = intArrayOf(111, 6, 182, 13, 188, 34, 168, 31, 138, 21)

    /**
     * 기본 서기 몸 소매 끝 윤곽선 가운데(x, y 꺾은선, 실기기 결함 DV-성-11). 팔 경계 사선이 소매 끝보다 아래라 그 사이 팔 피부가 톤을
     * 받지 않고 밝은 띠로 남았다. 이 x 범위에서는 윤곽선 아래만 팔이고, 윤곽선 위 소매 안 분홍 그늘은 옷으로 둔다.
     */
    private val legacyLeftHem = intArrayOf(
        80, 158, 88, 161, 96, 166, 104, 170, 112, 173, 120, 176, 128, 178, 136, 180, 140, 181
    )
    private val legacyRightHem = intArrayOf(
        374, 180, 378, 179, 386, 177, 394, 174, 402, 170, 410, 167, 418, 162, 422, 160, 428, 156
    )

    /** 팔 영역 위쪽 경계: [hem] 범위 안이면 소매 끝 윤곽선과 사선([diagonal]) 가운데 위쪽보다 아래, 밖이면 사선 아래. */
    private fun belowSleeve(x: Int, y: Int, diagonal: Double, hem: IntArray): Boolean {
        val hemY = lineY(x, hem) ?: return y >= diagonal
        return y > minOf(diagonal, hemY)
    }

    /** x 오름차순 꺾은선 [points]의 [x] 위치 y. 범위 밖이면 null. */
    private fun lineY(x: Int, points: IntArray): Double? {
        for (i in 0 until points.size - 2 step 2) {
            val x0 = points[i]
            val x1 = points[i + 2]
            if (x in x0..x1) return points[i + 1] + (points[i + 3] - points[i + 1]).toDouble() * (x - x0) / (x1 - x0)
        }
        return null
    }

    private fun inPolygon(x: Int, y: Int, points: IntArray): Boolean {
        var inside = false
        var previous = points.size - 2
        for (current in points.indices step 2) {
            val xi = points[current]
            val yi = points[current + 1]
            val xj = points[previous]
            val yj = points[previous + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi).toDouble() * (y - yi) / (yj - yi) + xi) inside = !inside
            previous = current
        }
        return inside
    }

    private fun decodeMutable(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inMutable = true })
            ?: throw IllegalArgumentException("unreadable split body layer")

    private fun encodePixels(pixels: IntArray, width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return try {
            AvatarImages.encode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
