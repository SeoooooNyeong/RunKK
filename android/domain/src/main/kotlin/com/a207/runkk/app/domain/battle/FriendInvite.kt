package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import java.time.Instant

/**
 * 내가 만든 친구 대결 초대 하나(D-270, POST /battles/friend-invites 응답). [inviteUrl]은 서버가 만든 링크
 * (`https://k15a207.p.ssafy.io/invite/battle/{token}`)이고 앱은 조립하지 않는다. [expiresAt]은 만든 때 + 5분이다.
 */
data class FriendInvite(
    val inviteId: Long,
    val token: String,
    val inviteUrl: String,
    val distance: Distance,
    val expiresAt: Instant
)
