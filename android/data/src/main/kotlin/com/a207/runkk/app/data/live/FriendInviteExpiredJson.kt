package com.a207.runkk.app.data.live

import com.a207.runkk.app.domain.live.FriendInviteExpired
import kotlinx.serialization.Serializable

/** 아무도 참가하지 않아 초대가 만료됐다(서버 battle.live.FriendInviteExpired, D-269). 초대한 앱에만 온다. */
@Serializable
internal data class FriendInviteExpiredJson(val inviteId: Long) {
    fun toDomain(): FriendInviteExpired = FriendInviteExpired(inviteId)
}
