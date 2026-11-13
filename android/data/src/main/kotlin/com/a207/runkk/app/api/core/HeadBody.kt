package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.FaceLayer

/**
 * 셀카 머리 응답 한 개(GET /avatar/heads/…)를 한 번 받아 둔 것. api.avatar가 domain.avatar.SelfieHead로 옮긴다(api.core는 도메인
 * 갈래 모델을 모른다).
 *
 * @property json 응답 JSON 그대로
 * @property faceLayers 같은 본문의 정면 세 층. 층 형식이 틀리면 빈 목록
 */
internal class HeadBody(val json: String, val faceLayers: List<FaceLayer>)
