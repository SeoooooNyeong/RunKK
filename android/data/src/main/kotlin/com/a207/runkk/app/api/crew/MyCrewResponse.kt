package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** GET /crews/me(서버 CrewDtos.MyCrewResponse). 소속 크루가 없으면 crew가 null이다. */
@Serializable
internal data class MyCrewResponse(
    val crew: CrewDetailResponse? = null,
    val pendingRequests: List<PendingJoin> = emptyList(),
    /** 24시간 가입·생성 제한 중이면 풀리는 시각(contract-W4 3절). */
    val rejoinAvailableAt: String? = null
) {
    @Serializable
    data class PendingJoin(val requestId: Long, val crewId: Long, val crewName: String, val requestedAt: String)
}
