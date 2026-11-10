package com.a207.runkk.app.core.avatar

/**
 * 화면이 지금 그리라고 한 값. 생성 머리는 참조로 비교한다(같은 인스턴스면 다시 합성하지 않는다). [mirrored]는 달리기 플레이어만
 * 좌우로 뒤집는 그림 변환 값이라 합성과 무관하다.
 */
internal data class AvatarRenderState(
    val gender: AvatarGender,
    val motion: AvatarMotion,
    val outfit: AvatarOutfit,
    val shape: AvatarBodyShape,
    val runningAmount: Int,
    val headLayers: GeneratedHeadLayers?,
    val mirrored: Boolean = false
) {
    val look: AvatarLookKey get() = AvatarLookKey(outfit, shape, runningAmount)
}
