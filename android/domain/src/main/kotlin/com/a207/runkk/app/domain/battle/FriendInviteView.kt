package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import java.time.Instant

/**
 * 받은 초대의 참가 확인 정보(D-273, GET /battles/friend-invites/by-token/{token}). 서버는 끝난 초대도 돌려주므로 화면은 [status]가
 * 아니라 [blockedReason](INVITE_OWN·INVITE_EXPIRED·INVITE_CANCELLED·INVITE_ALREADY_JOINED·ALREADY_IN_BATTLE, 참가할 수 있으면 null)으로
 * 고른다. 서버 응답의 me는 [myTier]·[myBestSeconds]로 펴 둔다. 최고 기록은 그 거리 기록(초)이고 없으면 null이다.
 */
data class FriendInviteView(
    val inviteId: Long,
    val status: String,
    val distance: Distance,
    val expiresAt: Instant,
    val inviter: Inviter,
    val myTier: String,
    val myBestSeconds: Long?,
    val blockedReason: String?
) {
    /** 초대한 러너. */
    data class Inviter(val name: String, val tier: String, val bestSeconds: Long?)
}
