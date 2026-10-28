package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/** 진행 중인 대결(GET /battles/active 200 본문). type·status는 서버 enum 이름 그대로, opponentName·startAt은 없으면 null이다. */
@Serializable
internal data class ActiveBattleResponse(
    val battleId: Long,
    val type: String,
    val status: String,
    val distanceMeters: Long,
    val opponentName: String? = null,
    val startAt: String? = null
)
