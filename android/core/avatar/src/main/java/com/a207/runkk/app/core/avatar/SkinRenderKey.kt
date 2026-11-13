package com.a207.runkk.app.core.avatar

/**
 * 피부·의상 합성 텍스처 캐시 키. 그 슬롯의 그림을 바꾸는 의상 부위만 담아 다른 부위를 바꿀 때 다시 그리지 않는다.
 * 체형([shape] 단계)은 체형 전용 파일(bodyOverrides)이 있을 수 있어 의상 슬롯 키에 넣는다.
 */
internal data class SkinRenderKey(
    val amount: Int,
    val top: Int = 0,
    val bottom: Int = 0,
    val shoes: Int = 0,
    val shape: Int = 0
) {
    companion object {
        fun of(
            assetName: String,
            amount: Int,
            outfit: AvatarOutfit,
            shape: AvatarBodyShape = AvatarBodyShape.BASE
        ): SkinRenderKey = when (OutfitSlot.of(assetName)) {
            OutfitSlot.STAND -> SkinRenderKey(amount, outfit.top, outfit.bottom, outfit.shoes, shape.stage)
            OutfitSlot.RUN_BODY, OutfitSlot.RUN_ARM -> SkinRenderKey(amount, top = outfit.top, shape = shape.stage)
            OutfitSlot.RUN_LEG -> SkinRenderKey(amount, bottom = outfit.bottom, shape = shape.stage)
            OutfitSlot.RUN_SHOE -> SkinRenderKey(amount, shoes = outfit.shoes, shape = shape.stage)
            null -> SkinRenderKey(amount)
        }
    }
}
