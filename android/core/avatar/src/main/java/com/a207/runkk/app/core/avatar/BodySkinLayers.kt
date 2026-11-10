package com.a207.runkk.app.core.avatar

/**
 * 몸 텍스처 슬롯 이름. 피부·옷을 색·좌표로 가려내던 판정은 의상을 PNG 레이어로 옮기며 지웠다(D-성-112, OUTFIT_SPEC.md).
 * 지금 몸은 맨몸 바탕(assets/outfits/body)과 의상 레이어를 [OutfitLayerComposer]가 겹친다.
 */
internal object BodySkinLayers {
    const val STANDING_BODY = "standing_body"
}
