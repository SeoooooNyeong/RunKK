package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/** 대결 결과(서버 BattleDtos.BattleResultResponse). recordId는 본인 러닝 기록 ID(앱이 만든 UUID), 아직 없으면 null. */
@Serializable
internal data class BattleResultResponse(
    val battleId: Long,
    val pending: Boolean = false,
    val verdict: String? = null,
    val rows: List<Row> = emptyList(),
    val rewardPoints: Int? = null,
    val newRecord: Boolean = false,
    val opponentName: String? = null,
    val recordId: String? = null,
    val myReachSeconds: Long? = null,
    val opponentReachSeconds: Long? = null,
    val weekly: Weekly? = null
) {
    @Serializable
    data class Row(val item: String, val mine: String, val theirs: String)

    /** 이번 주 전적(서버 battle.WeeklyRecord). */
    @Serializable
    data class Weekly(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0)
}
