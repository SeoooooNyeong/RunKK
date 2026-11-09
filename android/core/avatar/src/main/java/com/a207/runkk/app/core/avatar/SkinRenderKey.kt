package com.a207.runkk.app.core.avatar

/** 피부·의상 합성 텍스처 캐시 키. 그 자산의 그림을 바꾸는 의상 부위만 담아 다른 부위를 바꿀 때 다시 그리지 않는다. */
internal data class SkinRenderKey(val amount: Int, val top: Int = 0, val bottom: Int = 0, val shoes: Int = 0) {
    companion object {
        fun of(assetName: String, amount: Int, outfit: AvatarOutfit): SkinRenderKey = when (assetName) {
            BodySkinLayers.STANDING_BODY -> SkinRenderKey(amount, outfit.top, outfit.bottom, outfit.shoes)

            "continuous_arm", "side_body_coral", "side_body_cream", "side_body_blue" ->
                SkinRenderKey(amount, top = outfit.top)

            "side_leg_skin" -> SkinRenderKey(amount, bottom = outfit.bottom, shoes = outfit.shoes)

            "side_shoe" -> SkinRenderKey(amount, shoes = outfit.shoes)

            else -> SkinRenderKey(amount)
        }
    }
}
