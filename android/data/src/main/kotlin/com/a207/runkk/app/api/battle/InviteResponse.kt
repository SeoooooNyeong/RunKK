package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/**
 * 친구 대결 초대(서버 FriendInviteDtos.InviteResponse, D-270). POST /battles/friend-invites(201)와 GET /battles/friend-invites/mine(200)이
 * 준다. inviteUrl은 서버가 만든 링크이고 expiresAt은 오프셋 없는 LocalDateTime(Asia/Seoul)이다.
 */
@Serializable
internal data class InviteResponse(
    val inviteId: Long,
    val token: String,
    val inviteUrl: String,
    val distanceMeters: Long,
    val expiresAt: String
)
