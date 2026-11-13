package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** GET /crews/{id}/crew-runs/past(서버 CrewScheduleDtos.PastCrewRunsResponse, contract-W6). */
@Serializable
internal data class PastCrewRunsResponse(
    val month: String,
    val dates: List<String> = emptyList(),
    val runs: List<PastRun> = emptyList(),
    val earliestMonth: String? = null
) {
    @Serializable
    data class PastRun(
        val postId: Long,
        val title: String,
        val scheduledAt: String,
        val distanceMeters: Long? = null,
        val status: String,
        val goingCount: Int = 0,
        val runnerCount: Int = 0
    )
}
