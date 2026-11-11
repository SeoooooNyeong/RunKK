package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import timber.log.Timber

/**
 * 슬롯 텍스처 하나를 레이어로 겹친다(D-성-110, OUTFIT_SPEC.md 3절): 맨몸 바탕(피부톤 필터) → 신발 → 하의 → 상의.
 * 피부톤 필터는 맨몸 바탕에만 걸고 의상 그림은 원래 색 그대로 얹는다(D-성-112). 색·좌표로 피부를 가려내지 않는다.
 * 묶음 파일이 없으면 그 부위 base_* 파일, 그것도 없으면 그 레이어를 건너뛴다(자산 없음 경로).
 *
 * 상의 자르기([임시:D-성-156]): 상의가 있는 슬롯에서는 맨몸 바탕을 그리기 전에 상의 밖으로 비치는 얇은 피부 테두리를 지운다([TopBodyCut],
 * 자르기 칸 = [OutfitCatalog.cutFile]).
 */
internal object OutfitLayerComposer {
    private val ORDER = listOf(OutfitPart.SHOES, OutfitPart.BOTTOM, OutfitPart.TOP)

    /** 겹칠 레이어 하나. [part]가 TOP이면 맨몸 자르기의 기준이다. */
    class Layer(val part: OutfitPart, val path: String)

    /** 슬롯 [slot]의 이 모습에 들어갈 의상 파일(겹치는 순서). */
    fun layers(
        catalog: OutfitCatalog,
        slot: OutfitSlot,
        gender: AvatarGender,
        shape: AvatarBodyShape,
        outfit: AvatarOutfit,
        exists: (String) -> Boolean
    ): List<Layer> = ORDER.filter { it in slot.parts }.mapNotNull { part ->
        val id = when (part) {
            OutfitPart.TOP -> outfit.top
            OutfitPart.BOTTOM -> outfit.bottom
            OutfitPart.SHOES -> outfit.shoes
        }
        val chosen = catalog.bundle(part, id)?.file(gender, slot, shape)
        val base = catalog.bundle(part, OutfitCatalog.baseKey(part))?.file(gender, slot, shape)
        listOfNotNull(chosen, base).firstOrNull(exists)?.let { Layer(part, it) }
    }

    /** [layers]의 경로만(겹치는 순서). */
    fun layerFiles(
        catalog: OutfitCatalog,
        slot: OutfitSlot,
        gender: AvatarGender,
        shape: AvatarBodyShape,
        outfit: AvatarOutfit,
        exists: (String) -> Boolean
    ): List<String> = layers(catalog, slot, gender, shape, outfit, exists).map { it.path }

    /**
     * [body](맨몸 바탕, 신발 슬롯이면 null) 위에 [layers](겹치는 순서, 부위와 바이트)를 얹는다. 피부톤은 [amount]로 바탕에만.
     * [cut]은 상의 자르기 칸(없으면 자르지 않는다). [width]·[height]는 슬롯 크기.
     * 레이어 하나를 읽지 못하면(깨진 파일, 크기 다름) 그 부위는 [fallback](그 부위 base_* 바이트)으로 그리고, 그것도 못 읽으면 그 레이어만
     * 건너뛴다(로그를 남기고 전체는 실패하지 않는다). 맨몸 바탕을 읽지 못하면 IllegalArgumentException.
     */
    fun compose(
        body: ByteArray?,
        layers: List<Pair<OutfitPart, ByteArray>>,
        amount: Int,
        width: Int,
        height: Int,
        cut: ByteArray? = null,
        fallback: (OutfitPart) -> ByteArray? = { null }
    ): ByteArray {
        val decoded = mutableListOf<Pair<OutfitPart, Bitmap>>()
        var result: Bitmap? = null
        try {
            layers.forEach { (part, bytes) ->
                decodeLayer(part, bytes, fallback, width, height)?.let {
                    decoded +=
                        part to it
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result = bitmap
            val canvas = Canvas(bitmap)
            body?.let { drawBody(canvas, it, decoded, amount, cut) }
            decoded.forEach { (_, layer) -> canvas.drawBitmap(layer, 0f, 0f, null) }
            return AvatarImages.encode(bitmap)
        } finally {
            decoded.forEach { it.second.recycle() }
            result?.recycle()
        }
    }

    /** 맨몸 바탕에 상의 자르기와 피부톤을 걸어 [canvas]에 그린다. */
    private fun drawBody(
        canvas: Canvas,
        body: ByteArray,
        decoded: List<Pair<OutfitPart, Bitmap>>,
        amount: Int,
        cut: ByteArray?
    ) {
        val skin = decode(body, canvas.width, canvas.height)
        try {
            val top = decoded.lastOrNull { (part, _) -> part == OutfitPart.TOP }?.second
            val bottom = decoded.lastOrNull { (part, _) -> part == OutfitPart.BOTTOM }?.second
            if (top != null && cut != null) cutOutside(skin, top, bottom, cut)
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(SkinToneFilter.matrix(amount)) }
            canvas.drawBitmap(skin, 0f, 0f, if (amount == MIN_RUNNING_AMOUNT) null else paint)
        } finally {
            skin.recycle()
        }
    }

    /** 레이어 하나. 못 읽으면 [fallback], 그것도 못 읽으면 null(건너뜀). */
    private fun decodeLayer(
        part: OutfitPart,
        bytes: ByteArray,
        fallback: (OutfitPart) -> ByteArray?,
        width: Int,
        height: Int
    ): Bitmap? = try {
        decode(bytes, width, height)
    } catch (error: IllegalArgumentException) {
        Timber.w(error, "outfit layer %s unreadable, using base", part)
        try {
            fallback(part)?.let { decode(it, width, height) }
        } catch (baseError: IllegalArgumentException) {
            Timber.w(baseError, "base layer %s unreadable, skipped", part)
            null
        }
    }

    /** [skin]에서 상의(와 그 슬롯의 하의) 밖으로 비치는 얇은 테두리와 상의의 자르기 표지 칸을 지운다([TopBodyCut]). [skin]은 바뀐다. */
    private fun cutOutside(skin: Bitmap, top: Bitmap, bottom: Bitmap?, cut: ByteArray) {
        val width = skin.width
        val height = skin.height
        val area = decode(cut, width, height)
        val areaPixels = IntArray(width * height).also { area.getPixels(it, 0, width, 0, 0, width, height) }
        area.recycle()
        val topPixels = IntArray(width * height).also { top.getPixels(it, 0, width, 0, 0, width, height) }
        // 세트별 자르기 표지(알파 1)는 옷으로 치지 않고, 띠 자르기 뒤에 그 자리 맨몸을 지운다
        val marks = TopBodyCut.takeMarks(topPixels)
        // 표지는 그리는 상의 그림에서도 지운다(알파 1이라 거의 보이지 않지만 합성 텍스처에 남기지 않는다). 표지 칸만 바꿔 다른 픽셀의
        // 미리곱 반올림은 건드리지 않는다
        for (i in marks.indices) if (marks[i]) top.setPixel(i % width, i / width, 0)
        // 상의 칸으로 올라온 하의 허리(겹침 띠)도 옷으로 친다: 밑단과 허리 사이·허리 옆으로 맨몸이 비치지 않게
        bottom?.let {
            val bottomPixels = IntArray(width * height).also { b -> it.getPixels(b, 0, width, 0, 0, width, height) }
            for (i in topPixels.indices) if (topPixels[i] ushr 24 == 0) topPixels[i] = bottomPixels[i]
        }
        val skinPixels = IntArray(width * height).also { skin.getPixels(it, 0, width, 0, 0, width, height) }
        TopBodyCut.apply(skinPixels, topPixels, areaPixels, width, height)
        TopBodyCut.cutMarks(skinPixels, marks)
        skin.setPixels(skinPixels, 0, width, 0, 0, width, height)
    }

    /**
     * 원래 크기 그대로 읽는다. 줄이기(inSampleSize, inScaled·밀도 맞춤)를 쓰면 안 된다: 상의 레이어의 자르기 표지(알파 정확히 1,
     * [TopBodyCut.MARK_ALPHA])가 이웃과 섞여 사라지거나 다른 픽셀이 표지가 된다(OutfitCompositeTest가 표지 수를 본다).
     */
    private fun decode(bytes: ByteArray, width: Int, height: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inMutable = true }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalArgumentException("unreadable outfit layer")
        if (bitmap.width != width || bitmap.height != height) {
            bitmap.recycle()
            throw IllegalArgumentException("outfit layer must be ${width}x$height")
        }
        return bitmap
    }
}
