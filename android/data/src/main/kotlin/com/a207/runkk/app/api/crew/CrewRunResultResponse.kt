package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** 크루런 결과(서버 CrewDtos.CrewRunResultResponse, 합산 방식). */
@Serializable
internal data class CrewRunResultResponse(
    val postId: Long,
    val status: String? = null,
    val title: String? = null,
    val distanceMeters: Long? = null,
    val startedAt: String? = null,
    val totalDistanceMeters: Long? = null,
    val participants: List<Participant> = emptyList(),
    val myRecordId: String? = null,
    val firstCrewRun: Boolean = false,
    val raceContributionMeters: Long = 0
) {
    @Serializable
    data class Participant(
        val memberId: Long? = null,
        val displayName: String? = null,
        val distanceMeters: Long = 0,
        val elapsedSeconds: Long? = null,
        val averagePaceSecondsPerKm: Int? = null,
        val ended: Boolean = true,
        val me: Boolean = false,
        /** 아바타 얼굴 키. 얼굴을 고르지 않았거나 탈퇴·옛 서버면 null(계약 crewrun-sum v1.2). */
        val faceKey: String? = null,
        /** 표시 성별 MALE | FEMALE(D-327). 탈퇴·옛 서버면 null. */
        val shownGender: String? = null
    )
}
