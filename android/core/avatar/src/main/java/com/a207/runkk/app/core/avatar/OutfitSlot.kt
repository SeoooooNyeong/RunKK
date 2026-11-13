package com.a207.runkk.app.core.avatar

/** 의상 그림이 대응하는 riv 이미지 슬롯(OUTFIT_SPEC.md 5.2). */
internal enum class OutfitSlot(val fileName: String, val width: Int, val height: Int, val parts: Set<OutfitPart>) {
    STAND("stand", 512, 768, setOf(OutfitPart.TOP, OutfitPart.BOTTOM, OutfitPart.SHOES)),
    RUN_BODY("run_body", 256, 384, setOf(OutfitPart.TOP)),
    RUN_ARM("run_arm", 256, 1024, setOf(OutfitPart.TOP)),
    RUN_LEG("run_leg", 256, 768, setOf(OutfitPart.BOTTOM)),
    RUN_SHOE("run_shoe", 256, 160, setOf(OutfitPart.SHOES));

    companion object {
        /** riv 이미지 자산 이름 → 슬롯. 달리기 몸통 세 슬롯은 같은 그림을 쓴다(DR-성-4). 의상 슬롯이 아니면 null. */
        fun of(assetName: String): OutfitSlot? = when (assetName) {
            BodySkinLayers.STANDING_BODY -> STAND
            "side_body_coral", "side_body_cream", "side_body_blue" -> RUN_BODY
            "continuous_arm" -> RUN_ARM
            "side_leg_skin" -> RUN_LEG
            "side_shoe" -> RUN_SHOE
            else -> null
        }
    }
}

/** 자산 파일 이름의 성별 부분(female·male). */
internal val AvatarGender.fileName: String get() = if (this == AvatarGender.FEMALE) "female" else "male"
