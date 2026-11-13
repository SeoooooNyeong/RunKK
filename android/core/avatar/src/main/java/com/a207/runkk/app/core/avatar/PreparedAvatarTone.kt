package com.a207.runkk.app.core.avatar

/** 합성 스레드가 만든 텍스처 묶음. 주 스레드가 Rive 이미지 자리에 넣고 캐시에 합친다. */
internal class PreparedAvatarTone(
    val request: AvatarToneRequest,
    val face: AvatarToneCache.RenderedFace?,
    val hair: ByteArray?,
    val skinBases: Map<String, ByteArray>,
    val skinRenders: Map<String, AvatarToneCache.RenderedSkin>,
    val frontOverlay: AvatarToneCache.RenderedOverlay?,
    val runOverlay: AvatarToneCache.RenderedOverlay?
) {
    /** Rive 이미지 자리 이름 → 넣을 PNG. */
    val textures: Map<String, ByteArray> get() = skinRenders.mapValues { it.value.bytes }

    fun toCache(previous: AvatarToneCache): AvatarToneCache = AvatarToneCache(
        skinBases = previous.skinBases + skinBases,
        skinRenders = previous.skinRenders + skinRenders,
        face = face ?: previous.face,
        frontOverlay = frontOverlay ?: previous.frontOverlay,
        runOverlay = runOverlay ?: previous.runOverlay
    )
}
