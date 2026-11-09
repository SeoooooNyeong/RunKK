package com.a207.runkk.app.core.avatar

import androidx.annotation.WorkerThread

/**
 * 셀카로 생성한 머리를 남녀 Rive 몸체에 붙일 수 있게 바꾼 결과. [RunkkRiveAvatar]의 headLayers로 넘긴다(null이면 내장 기본 머리).
 *
 * 같은 인스턴스를 다시 넘기면 다시 합성하지 않는다(참조로 비교한다). 화면은 결과 하나당 한 번만 만들어 상태로 들고 있는다.
 */
class GeneratedHeadLayers internal constructor(
    internal val variants: Map<AvatarGender, AvatarVariantAssets>,
    /** 생성 얼굴의 피부색(불투명 ARGB). 몸 피부 바탕을 이 색에 맞춘다. */
    internal val skinColor: Int
) {
    companion object {
        /**
         * 셀카 생성 결과 JSON(faceImage·eyesImage·hairImage·rightFaceImage·rightEyesImage·rightHairImage, 선택 frontImage·
         * rightImage, 선택 mobile.neckMask·mobile.skinColor)을 바꾼다. 모든 PNG는 1024×1024 Data URL이다.
         *
         * 그림을 여러 장 풀고 다시 인코딩하므로(수백 ms~수 초) 주 스레드에서 부르지 않는다. 형식이 맞지 않거나 목·눈을 찾지
         * 못하면 IllegalArgumentException이다. 화면은 실패하면 기본 머리(null)를 그린다.
         */
        @WorkerThread
        fun fromHeadJson(json: String): GeneratedHeadLayers =
            GeneratedAvatarLayers.prepare(GeneratedHeadInput.parse(json))
    }
}
