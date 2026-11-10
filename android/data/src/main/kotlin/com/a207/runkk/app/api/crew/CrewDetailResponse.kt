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
    // contract-W4 1절(S15P31A207-295): 개설일, 누적 거리·크루런, 최근 크루런 3건, 24시간 가입 제한 시각
    val createdAt: String? = null,
    val totalDistanceMeters: Long = 0,
    val crewRunCount: Int = 0,
    val recentCrewRuns: List<RecentCrewRun> = emptyList(),
    val rejoinAvailableAt: String? = null
) {
    /** 최근 마친 크루런 한 건(서버 CrewDtos.CrewDetailResponse.RecentCrewRun). */
    @Serializable
    data class RecentCrewRun(
        val postId: Long,
        val scheduledAt: String,
        val participantCount: Int,
        val totalDistanceMeters: Long
    )
}
