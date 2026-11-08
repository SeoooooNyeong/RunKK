package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** GET /crews/{id}, GET /crews/me의 crew(서버 CrewDtos.CrewDetailResponse). 초대 코드는 크루원 모두에게 온다(크루 밖이면 null). */
@Serializable
internal data class CrewDetailResponse(
    val crewId: Long,
    val name: String,
    val intro: String? = null,
    val tags: Set<String> = emptySet(),
    val visibility: String,
    val joinPolicy: String,
    val logoUrl: String? = null,
    val leaderId: Long,
    val leaderName: String? = null,
    val headcount: Int,
    val weeklyDistanceMeters: Long = 0,
    val lastWeekDistanceMeters: Long = 0,
    val myRole: String? = null,
    val inviteCode: String? = null,
    val inviteCodeExpiresAt: String? = null
)
