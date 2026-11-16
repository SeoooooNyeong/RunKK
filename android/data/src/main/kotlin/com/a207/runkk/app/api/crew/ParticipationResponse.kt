package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** 서버 CrewDtos.ParticipationResponse(POST /crew-runs/{id}/join, 설계 05-32 5.2). 다시 불러도 내 joinedAt·measurementRecordId가 같다. */
@Serializable
internal data class ParticipationResponse(
    val postId: Long,
    val crewId: Long,
    val revision: Long = 0,
    val joinedAt: String,
    val measurementRecordId: String,
    val alreadyJoined: Boolean = false,
    val participants: List<Runner> = emptyList()
) {
    /** 진행 참가자 한 줄(참여 순). me는 본인 줄이다. */
    @Serializable
    data class Runner(
        val memberId: Long? = null,
        val displayName: String? = null,
        val joinedAt: String? = null,
        val ended: Boolean = false,
        val me: Boolean = false
    )
}
