package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/** 대결 이력(서버 BattleDtos.BattleHistoryResponse). nextCursor는 size를 보냈을 때 다음 쪽 커서이고 끝이면 null이다. */
@Serializable
internal data class BattleHistoryResponse(
    val entries: List<Entry> = emptyList(),
    val total: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val selfNewRecords: Int = 0,
    val nextCursor: Long? = null
) {
    /** gapSeconds는 본인 − 상대(초, 음수면 본인이 빠름), 없으면 null. sourceLabel은 유명인 기록 출처 표기. */
    @Serializable
    data class Entry(
        val battleId: Long,
        val type: String,
        val opponentName: String,
        val distanceMeters: Long,
        val verdict: String? = null,
        val playedAt: String,
        val newRecord: Boolean = false,
        val gapSeconds: Long? = null,
        val sourceLabel: String? = null
    )
}
