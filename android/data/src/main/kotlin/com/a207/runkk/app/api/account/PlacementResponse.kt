package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/** 본인 배치고사 진행(GET /me/placement, 05-03 5.7.3.3 P1). 서버 MeController.PlacementResponse와 항목이 같다. */
@Serializable
internal data class PlacementResponse(
    val status: String,
    val countedRounds: Int,
    val nextRound: Int? = null,
    val provisionalTier: String? = null,
    val tier: String,
    val rounds: List<Round> = emptyList(),
    /** 확정된 Runimal 유형 이름(없거나 옛 서버면 null). */
    val runimalType: String? = null
) {
    /** 센 판 하나. 거리는 미터, 시간은 초, 페이스는 km당 초. */
    @Serializable
    data class Round(
        val round: Int,
        val battleId: Long,
        val battleDistanceMeters: Long,
        val distanceMeters: Long,
        val elapsedSeconds: Long,
        val averagePaceSecondsPerKm: Int? = null,
        val verdict: String
    )
}
