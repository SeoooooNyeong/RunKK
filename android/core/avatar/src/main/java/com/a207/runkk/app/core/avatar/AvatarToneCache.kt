package com.a207.runkk.app.core.avatar

/**
 * 합성 결과 캐시(주 스레드 소유, 합성 스레드에는 읽기 전용 사본으로 넘긴다). 맨몸 바탕(생성 머리면 피부색을 맞춘 사본)은 정체(남녀·머리)가
 * 같은 동안, 텍스처는 키(러닝량·관련 의상·체형)가 같은 동안 다시 쓴다. 정체가 바뀌면 새 캐시로 시작한다.
 */
internal class AvatarToneCache(
    /** riv 자리 이름 → 이 정체에 맞춘 맨몸 바탕 PNG(assets/outfits/body, 생성 머리면 피부색을 맞춘 사본). */
    val skinBases: Map<String, ByteArray> = emptyMap(),
    val skinRenders: Map<String, RenderedSkin> = emptyMap(),
    val face: RenderedFace? = null,
    val frontOverlay: RenderedOverlay? = null,
    val runOverlay: RenderedOverlay? = null
) {
    class RenderedSkin(val key: SkinRenderKey, val bytes: ByteArray)

    /** 러닝량 톤을 준 정면 피부 얼굴과 그 얼굴에 맞춘 헤어(경계만 톤, DV-성-5). 헤어가 없는 요청이면 [hair]는 null. */
    class RenderedFace(val amount: Int, val bytes: ByteArray, val hair: ByteArray? = null)

    class RenderedOverlay(val shape: AvatarBodyShape, val amount: Int, val bytes: ByteArray)
}
