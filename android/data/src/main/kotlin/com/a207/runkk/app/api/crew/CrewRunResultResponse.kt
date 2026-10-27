package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

@Serializable
internal data class CrewRunResultResponse(
    val postId: Long,
    val finishers: List<Finisher> = emptyList(),
    val firstCrewRun: Boolean = false,
    val raceContributionMeters: Long = 0
) {
    @Serializable
    data class Finisher(
        val rank: Int? = null,
        val displayName: String? = null,
        val elapsedSeconds: Long? = null,
        val averagePaceSecondsPerKm: Int? = null,
        val quit: Boolean = false,
        val memberId: Long? = null,
        val me: Boolean = false
    )
}
