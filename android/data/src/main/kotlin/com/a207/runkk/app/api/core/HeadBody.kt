package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.FaceLayer

/**
 * 셀카 머리 한 개를 층별로 받아 둔 것(S15P31A207-300 레이어 합성). api.avatar가 domain.avatar.SelfieHead로 옮긴다(api.core는 도메인
 * 갈래 모델을 모른다).
 *
 * @property faceLayers 정면 세 층(얼굴·눈·머리카락). 층 형식이 틀리면 빈 목록
 * @property rightLayers 옆모습 세 층(얼굴·눈·머리카락 순서). 없으면 빈 목록
 * @property meta 이미지 없는 메타데이터 JSON(합성 정보 mobile)
 */
internal class HeadBody(val faceLayers: List<FaceLayer>, val rightLayers: List<ByteArray>, val meta: String)
