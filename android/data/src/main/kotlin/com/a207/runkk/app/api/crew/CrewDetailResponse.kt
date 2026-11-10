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
    val inviteCodeExpiresAt: String? = null,
    /** 개설일·누적 거리·누적 크루런 횟수(contract-W4 1장, W7 크루 정보 F-18). 옛 서버면 없다. */
    val createdAt: String? = null,
    val totalDistanceMeters: Long? = null,
    val crewRunCount: Int? = null
)
