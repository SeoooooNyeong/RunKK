package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 홈·러닝 중 칸이 그리는 내 아바타 모습(D-735).
 *
 * @property rive 렌더러 모습(성별·의상·체형·러닝량·생성 머리). 미리 준비([prepareRiveAvatarLook][com.a207.runkk.app.core.avatar.prepareRiveAvatarLook])에
 *   넘긴 것과 같은 인스턴스라 머리 레이어 참조가 같다
 * @property backgroundKey 고른 배경 키(FR-202). null이면 기본 배경
 * @property faceKey 얼굴 키. Rive를 그리지 못할 때 앞 단계 그림에 쓴다
 * @property faceLayers 앞 단계 얼굴 그림(Rive 실패 때)
 */
data class MyAvatarLook(
    val rive: RiveAvatarLook,
    val backgroundKey: String?,
    val faceKey: String?,
    val faceLayers: List<FaceLayer>
)
