package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/** 친구 대결 참가 응답(서버 FriendInviteDtos.AcceptResponse, D-274). 뒤이어 실시간 MatchFound·BattleStart가 온다. */
@Serializable
internal data class AcceptResponse(val battleId: Long)
