package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** 서버 CrewDtos.LobbyResponse. 모집 일시는 scheduledAt, 크루런 거리는 distanceMeters다. */
@Serializable
internal data class LobbyResponse(
    val postId: Long,
    val participants: List<Participant> = emptyList(),
    val scheduledAt: String,
    val distanceMeters: Long
) {
    /** me는 열람자 본인 줄이다. */
    @Serializable
    data class Participant(
        val memberId: Long? = null,
        val displayName: String? = null,
        val ready: Boolean = false,
        val me: Boolean = false
    )
}
