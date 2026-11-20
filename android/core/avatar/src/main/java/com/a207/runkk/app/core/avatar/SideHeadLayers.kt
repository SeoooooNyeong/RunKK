package com.a207.runkk.app.core.avatar

/**
 * 같은 1024 캔버스에 정합된 측면 머리 세 층(PoC SideHeadLayers). 얼굴이 피부 바탕 전체를 갖고, 눈·헤어는 톤을 거치지 않는다.
 * [neckRegistration]은 내장 샘플 옆얼굴에만 있고 생성 머리는 null이다. [neckUnderlay]는 얼굴 아래에 깔 몸 쪽 목 층이다(합성할 때
 * 목 등록 뒤 자리에 그린다).
 */
internal class SideHeadLayers(
    val face: ByteArray,
    val eyes: ByteArray,
    val hair: ByteArray,
    val neckRegistration: SideNeckRegistration? = null,
    val neckUnderlay: NeckUnderlay? = null
)
