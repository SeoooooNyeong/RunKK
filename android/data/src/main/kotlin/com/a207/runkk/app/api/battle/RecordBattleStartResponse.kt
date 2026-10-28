package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/** 기록 대결 시작 정보(design-review.md M34). track은 실시간 BattleStart.opponentTrack과 같은 형식이다. */
@Serializable
internal data class RecordBattleStartResponse(
    val battleId: Long,
    val opponentName: String,
    val distanceMeters: Long,
    val track: List<TrackPointDto> = emptyList(),
    val cardKind: String
)
