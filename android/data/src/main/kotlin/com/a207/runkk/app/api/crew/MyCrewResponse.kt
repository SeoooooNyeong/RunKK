package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** GET /crews/me(서버 CrewDtos.MyCrewResponse). 소속 크루가 없으면 crew가 null이다. */
@Serializable
internal data class MyCrewResponse(
    val crew: CrewDetailResponse? = null,
    val pendingRequests: List<PendingJoin> = emptyList()
) {
    @Serializable
    data class PendingJoin(val requestId: Long, val crewId: Long, val crewName: String, val requestedAt: String)
}
