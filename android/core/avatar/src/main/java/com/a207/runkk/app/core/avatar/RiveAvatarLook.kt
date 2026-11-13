package com.a207.runkk.app.core.avatar

/** 그릴 내 아바타 모습([RiveAvatarStage]). [headLayers]는 참조로 비교하므로 결과 하나당 한 번 만들어 둔다. */
data class RiveAvatarLook(
    val gender: AvatarGender,
    val outfit: AvatarOutfit,
    val bodyShape: AvatarBodyShape,
    val runningAmount: Int,
    val headLayers: GeneratedHeadLayers?
)
