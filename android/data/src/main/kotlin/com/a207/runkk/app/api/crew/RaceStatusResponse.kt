package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** 서버 CrewDtos.RaceStatusResponse. 공동 목표·진행률·달성 일시·축하 카드는 v0.11에서 더했다(D-105, FR-138~FR-141). */
@Serializable
internal data class RaceStatusResponse(
    val totalMeters: Long,
    val closesAt: String,
    val contributors: List<Contributor> = emptyList(),
    val myShare: Double? = null,
    val goalMeters: Long? = null,
    val goalProgressPercent: Int? = null,
    val achievedAt: String? = null,
    val celebration: Celebration? = null
) {
    @Serializable
    data class Celebration(val crewName: String, val totalMeters: Long, val goalMeters: Long, val participantCount: Int)

    @Serializable
    data class Contributor(
        val displayName: String? = null,
        val distanceMeters: Long,
        val share: Double,
        val memberId: Long? = null,
        val me: Boolean = false
    )
}
