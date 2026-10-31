package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/**
 * 받은 초대의 참가 확인 정보(서버 FriendInviteDtos.InviteViewResponse, D-273). blockedReason이 null이면 참가할 수 있다.
 * 최고 기록(bestSeconds)은 그 거리 기록(초)이고 없으면 null이다.
 */
@Serializable
internal data class InviteViewResponse(
    val inviteId: Long,
    val status: String,
    val distanceMeters: Long,
    val expiresAt: String,
    val inviter: Inviter,
    val me: Me,
    val blockedReason: String? = null
) {
    @Serializable
    data class Inviter(val name: String, val tier: String, val bestSeconds: Long? = null)

    @Serializable
    data class Me(val tier: String, val bestSeconds: Long? = null)
}
