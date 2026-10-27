package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome

/**
 * 아바타(서버 avatar.AvatarView, FR-155~FR-163). 얼굴이 없으면 faceKey가 null이고 "기본 캐릭터를 고를 수 있다".
 * balance는 리워드 잔액이다. 필드는 10/21 API 계약으로 확정한다.
 */
data class Avatar(
    val faceKey: String?,
    val defaultCharacterId: String?,
    val bodyShape: String,
    val skinTone: String,
    val equipped: Map<ItemSlot, Long>,
    val balance: Int,
    val canCreateSelfieToday: Boolean,
    val rejected: Map<ItemSlot, String>,
    /** 고른 배경 키(FR-202). null이면 단계별 기본 배경이다. */
    val backgroundKey: String? = null
)
