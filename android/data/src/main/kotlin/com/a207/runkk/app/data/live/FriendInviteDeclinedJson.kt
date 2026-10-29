package com.a207.runkk.app.data.live

import com.a207.runkk.app.domain.live.FriendInviteDeclined
import kotlinx.serialization.Serializable

/** 친구가 초대를 거절했다(서버 battle.live.FriendInviteDeclined, D-269). 초대한 앱에만 온다. */
@Serializable
internal data class FriendInviteDeclinedJson(val inviteId: Long) {
    fun toDomain(): FriendInviteDeclined = FriendInviteDeclined(inviteId)
}
