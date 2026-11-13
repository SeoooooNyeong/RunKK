package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.FaceLayer

/**
 * 셀카 머리 결과 한 개(GET /avatar/heads/{회원ID}/{작업ID}, contract-W8.md 6.2). 본문을 한 번만 받아 렌더러 입력과 대체 그림을 함께 쓴다.
 *
 * @property json 응답 JSON 그대로(W9 Rive 렌더러 머리 레이어 입력)
 * @property faceLayers 같은 본문의 정면 세 층(얼굴·눈·머리카락). Rive를 그리지 못할 때 앞 단계 그림이 쓴다. 층이 비었거나 PNG가
 *   아니면 빈 목록이다
 */
class SelfieHead(val json: String, val faceLayers: List<FaceLayer>)
