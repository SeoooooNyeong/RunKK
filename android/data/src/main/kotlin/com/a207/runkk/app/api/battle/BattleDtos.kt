package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

// 대결 REST 전송 형식(서버 battle …View). 거리는 미터, 시간은 초, 시각은 ServerTime, 날짜는 yyyy-MM-dd.

@Serializable
internal data class MatchRequest(val distanceMeters: Long)

@Serializable
internal data class MatchAcceptanceResponse(val waiting: Boolean, val distanceMeters: Long? = null)

/** 나와의 싸움·유명인 후보(서버 battle.RecordBattleCandidates). id는 유명인 기록 ID(나와의 싸움은 null). */
@Serializable
internal data class CandidatesResponse(val candidates: List<Candidate> = emptyList()) {
    @Serializable
    data class Candidate(
        val id: Long? = null,
        val displayName: String,
        val distanceMeters: Long,
        val finishSeconds: Long? = null,
        val recordedOn: String? = null,
        val sourceLabel: String? = null,
        val estimated: Boolean = false
    )
}

@Serializable
internal data class SelfBattleRequest(val distanceMeters: Long)

@Serializable
internal data class CelebrityBattleRequest(val celebrityId: Long)

/** 기록 대결 시작 정보(design-review.md M34). track은 실시간 BattleStart.opponentTrack과 같은 형식이다. */
@Serializable
internal data class RecordBattleStartResponse(
    val battleId: Long,
    val opponentName: String,
    val distanceMeters: Long,
    val track: List<TrackPointDto> = emptyList(),
    val cardKind: String
)

@Serializable
internal data class TrackPointDto(val elapsedSeconds: Long, val distanceMeters: Long)

@Serializable
internal data class BattleResultResponse(
    val battleId: Long,
    val pending: Boolean = false,
    val verdict: String? = null,
    val rows: List<Row> = emptyList(),
    val rewardPoints: Int? = null,
    val newRecord: Boolean = false
) {
    @Serializable
    data class Row(val item: String, val mine: String, val theirs: String)
}

@Serializable
internal data class BattleHistoryResponse(
    val entries: List<Entry> = emptyList(),
    val total: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val selfNewRecords: Int = 0
) {
    @Serializable
    data class Entry(
        val battleId: Long,
        val type: String,
        val opponentName: String,
        val distanceMeters: Long,
        val verdict: String? = null,
        val playedAt: String
    )
}
