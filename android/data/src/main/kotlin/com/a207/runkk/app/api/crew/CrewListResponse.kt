package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** GET /crews(서버 CrewDtos.CrewListResponse). nextCursor는 size를 보냈을 때 다음 쪽 커서이고 끝이면 null이다. */
@Serializable
internal data class CrewListResponse(
    val crews: List<Item> = emptyList(),
    val nextCursor: Long? = null,
    /** 같은 조건의 공개 크루 전체 수(contract-W4 2절). */
    val totalCount: Long = 0
) {
    @Serializable
    data class Item(
        val crewId: Long,
        val name: String,
        val intro: String? = null,
        val tags: Set<String> = emptySet(),
        val logoUrl: String? = null,
        val leaderName: String? = null,
        val headcount: Int,
        val weeklyDistanceMeters: Long = 0,
        val joinPolicy: String
    )
}
