package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/**
 * GET·PATCH /crew-runs/{id} 응답(서버 CrewScheduleDtos.CrewRunDetailResponse, 계약 contract-W6.md 2.3). distanceMeters가 null이면
 * 제한 없음, myAnswer가 null이면 응답하지 않았거나 참석을 취소했다. repeat은 NONE·WEEKLY, repeatDayOfWeek는 MONDAY~SUNDAY.
 */
@Serializable
internal data class CrewRunDetailResponse(
    val postId: Long,
    val crewId: Long,
    val title: String,
    val body: String = "",
    val authorId: Long? = null,
    val authorName: String? = null,
    val mine: Boolean = false,
    val writtenAt: String,
    val scheduledAt: String,
    val voteDeadline: String? = null,
    val distanceMeters: Long? = null,
    val status: String? = null,
    val goingCount: Int = 0,
    val myAnswer: String? = null,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
    val repeat: String? = null,
    val repeatDayOfWeek: String? = null,
    val attendees: List<Attendee> = emptyList()
) {
    /** 참석자. memberId는 탈퇴했으면 null, shownGender는 MALE·FEMALE이거나 null. */
    @Serializable
    data class Attendee(
        val memberId: Long? = null,
        val displayName: String? = null,
        val author: Boolean = false,
        val me: Boolean = false,
        val faceKey: String? = null,
        val shownGender: String? = null
    )
}

/**
 * PATCH /crew-runs/{id} 요청(서버 CrewScheduleDtos.CrewRunEditRequest). 수정 화면의 네 칸을 모두 보낸다(빠진 칸을 "그대로"로 보지
 * 않는다). distanceMeters가 null이면 제한 없음이다.
 */
@Serializable
internal data class CrewRunEditRequest(
    val title: String,
    val body: String?,
    val distanceMeters: Long?,
    val repeat: String
)
