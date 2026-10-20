package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

// 크루 REST 전송 형식(서버 crew …View). 거리는 미터, 시간은 초, 시각은 ServerTime, 날짜는 yyyy-MM-dd.

@Serializable
internal data class CreatedResponse(val id: Long)

@Serializable
internal data class CrewFormDto(
    val name: String,
    val intro: String?,
    val tags: Set<String>,
    val visibility: String,
    val joinPolicy: String
)

@Serializable
internal data class InviteCodeResponse(val inviteCode: String)

@Serializable
internal data class LeaderRequest(val memberId: Long)

/** POST /crews/join: 크루 ID로 신청하거나 초대 코드로 신청한다(MembershipService.apply). */
@Serializable
internal data class JoinRequestBody(val crewId: Long? = null, val inviteCode: String? = null)

@Serializable
internal data class CrewProfileRequest(val intro: String)

@Serializable
internal data class CrewListResponse(val crews: List<Item> = emptyList()) {
    @Serializable
    data class Item(
        val crewId: Long,
        val name: String,
        val intro: String? = null,
        val tags: Set<String> = emptySet(),
        val leaderName: String,
        val headcount: Int,
        val weeklyDistanceMeters: Long = 0,
        val joinPolicy: String
    )
}

@Serializable
internal data class CrewMembersResponse(val members: List<Member> = emptyList()) {
    @Serializable
    data class Member(
        val memberId: Long,
        val displayName: String,
        val role: String,
        val crewIntro: String? = null,
        val tier: String? = null,
        val runimalType: String? = null
    )
}

@Serializable
internal data class PendingRequestsResponse(val requests: List<Request> = emptyList()) {
    @Serializable
    data class Request(val requestId: Long, val memberId: Long, val displayName: String, val requestedAt: String)
}

@Serializable
internal data class CrewRankingResponse(val entries: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(val rank: Int, val displayName: String, val distanceMeters: Long)
}

@Serializable
internal data class RaceStatusResponse(
    val totalMeters: Long,
    val closesAt: String,
    val contributors: List<Contributor> = emptyList(),
    val myShare: Double? = null
) {
    @Serializable
    data class Contributor(val displayName: String, val distanceMeters: Long, val share: Double)
}

@Serializable
internal data class PostListResponse(val posts: List<Item> = emptyList()) {
    @Serializable
    data class Item(
        val postId: Long,
        val kind: String,
        val authorName: String,
        val summary: String,
        val writtenAt: String,
        val mine: Boolean = false
    )
}

/** 응원글(CHEER)은 body만, 크루런 모집글(CREW_RUN)은 title·body·distanceMeters·meetAt을 싣는다. */
@Serializable
internal data class PostWriteRequest(
    val kind: String,
    val title: String? = null,
    val body: String? = null,
    val distanceMeters: Long? = null,
    val meetAt: String? = null
)

@Serializable
internal data class VoteRequest(val answer: String)

@Serializable
internal data class LobbyResponse(val postId: Long, val participants: List<Participant> = emptyList(), val meetAt: String) {
    @Serializable
    data class Participant(val displayName: String, val ready: Boolean)
}

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
        val displayName: String,
        val elapsedSeconds: Long? = null,
        val averagePaceSecondsPerKm: Int? = null
    )
}
