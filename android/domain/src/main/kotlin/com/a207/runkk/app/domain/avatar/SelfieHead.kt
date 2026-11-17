package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.FaceLayer

/**
 * 셀카 머리 한 개(S15P31A207-300 레이어 합성). 서버 GET /avatar/heads/{회원ID}/{작업ID}/meta 와 …/layers/{층}?size=1024로 받은
 * 층(예전 서버면 통짜 응답 GET /avatar/heads/{회원ID}/{작업ID}에서 꺼낸 층)이다. 한 번 받아 렌더러 입력과 대체 그림을 함께 쓴다.
 *
 * @property faceLayers 정면 세 층(얼굴·눈·머리카락, 1024 PNG). Rive를 그리지 못할 때 앞 단계 그림이 쓴다. 층이 비었거나 PNG가
 *   아니면 빈 목록이다
 * @property rightLayers 옆모습 세 층(얼굴·눈·머리카락 순서, 1024 PNG). 예전 머리라 없으면 빈 목록이다(렌더러는 기본 머리를 그린다)
 * @property meta 이미지 없는 머리 메타데이터 JSON(mobile.neckMask·mobile.skinColor 등 합성 정보)
 */
class SelfieHead(
    val faceLayers: List<FaceLayer>,
    val rightLayers: List<ByteArray> = emptyList(),
    val meta: String = "{}"
)
