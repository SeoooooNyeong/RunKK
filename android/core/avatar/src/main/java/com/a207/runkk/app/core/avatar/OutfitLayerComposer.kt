package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * 슬롯 텍스처 하나를 레이어로 겹친다(D-성-110, OUTFIT_SPEC.md 5.3·5.4): 맨몸 바탕(피부톤 필터) → 신발 → 하의 → 상의.
 * 피부톤 필터는 맨몸 바탕에만 걸고 의상 PNG는 원래 색 그대로 얹는다(D-성-112). 색·좌표로 피부를 가려내지 않는다.
 * 묶음 파일이 없으면 그 부위 base_* 파일, 그것도 없으면 그 레이어를 건너뛴다(자산 없음 경로).
 */
internal object OutfitLayerComposer {
    private val ORDER = listOf(OutfitPart.SHOES, OutfitPart.BOTTOM, OutfitPart.TOP)

    /** 슬롯 [slot]의 이 모습에 들어갈 의상 파일 경로(겹치는 순서). */
    fun layerFiles(
        catalog: OutfitCatalog,
        slot: OutfitSlot,
        gender: AvatarGender,
        shape: AvatarBodyShape,
        outfit: AvatarOutfit,
        exists: (String) -> Boolean
    ): List<String> = ORDER.filter { it in slot.parts }.mapNotNull { part ->
        val id = when (part) {
            OutfitPart.TOP -> outfit.top
            OutfitPart.BOTTOM -> outfit.bottom
            OutfitPart.SHOES -> outfit.shoes
        }
        val chosen = catalog.bundle(part, id)?.file(gender, slot, shape)
        val base = catalog.bundle(part, OutfitCatalog.baseKey(part))?.file(gender, slot, shape)
        listOfNotNull(chosen, base).firstOrNull(exists)
    }

    /**
     * [body](맨몸 바탕 PNG, 신발 슬롯이면 null) 위에 [layers](PNG, 겹치는 순서)를 얹는다. 피부톤은 [amount]로 바탕에만.
     * [width]·[height]는 슬롯 크기.
     */
    fun compose(body: ByteArray?, layers: List<ByteArray>, amount: Int, width: Int, height: Int): ByteArray {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            body?.let {
                val skin = decode(it, width, height)
                try {
                    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(SkinToneFilter.matrix(amount)) }
                    canvas.drawBitmap(skin, 0f, 0f, if (amount == MIN_RUNNING_AMOUNT) null else paint)
                } finally {
                    skin.recycle()
                }
            }
            layers.forEach { bytes ->
                val layer = decode(bytes, width, height)
                try {
                    canvas.drawBitmap(layer, 0f, 0f, null)
                } finally {
                    layer.recycle()
                }
            }
            return AvatarImages.encode(result)
        } finally {
            result.recycle()
        }
    }

    private fun decode(bytes: ByteArray, width: Int, height: Int): Bitmap {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("unreadable outfit layer")
        if (bitmap.width != width || bitmap.height != height) {
            bitmap.recycle()
            throw IllegalArgumentException("outfit layer must be ${width}x$height")
        }
        return bitmap
    }
}
