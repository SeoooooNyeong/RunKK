package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap

/**
 * 여자 몸체 의상 텍스처를 승인된 리그 텍스처 좌표에 그린다(PoC OutfitStyles). 리그는 바꾸지 않는다.
 * 신발 위에 하의, 하의 허리 위에 상의 순서로 그린다. 번호별 이름은 res/values/avatar_outfits.xml에 있다.
 */
internal object OutfitStyles {
    fun apply(assetName: String, skin: Bitmap, clothing: Bitmap, selection: AvatarOutfit) {
        if (selection.isDefault || assetName == "side_head") return
        FootwearTypes.apply(assetName, skin, clothing, selection.shoes)
        BottomTypes.apply(assetName, skin, clothing, selection.bottom)
        TopTypes.apply(assetName, skin, clothing, selection.top)
    }
}
