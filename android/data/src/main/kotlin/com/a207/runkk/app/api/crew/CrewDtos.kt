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
        val leaderName: String? = null,
        val headcount: Int,
        val weeklyDistanceMeters: Long = 0,
        val joinPolicy: String
    )
}

/** GET /crews/recommendations 응답(서버 CrewDtos.RecommendationsResponse). 목록(CrewListResponse)과 달리 크루장·주간 거리·소개가 없다. */
@Serializable
internal data class RecommendationsResponse(val crews: List<Item> = emptyList(), val guide: String? = null) {
    @Serializable
    data class Item(
        val crewId: Long,
        val name: String,
        val matchCount: Int = 0,
        val tags: Set<String> = emptySet(),
        val headcount: Int,
        val joinPolicy: String
    )
}

@Serializable
internal data class CrewMembersResponse(val members: List<Member> = emptyList()) {
    @Serializable
    data class Member(
        val memberId: Long,
        val displayName: String? = null,
        val role: String,
        val crewIntro: String? = null,
        val tier: String? = null,
        val runimalType: String? = null
    )
}

@Serializable
internal data class PendingRequestsResponse(val requests: List<Request> = emptyList()) {
    @Serializable
    data class Request(
        val requestId: Long,
        val memberId: Long,
        val displayName: String? = null,
        val requestedAt: String
    )
}

@Serializable
internal data class CrewRankingResponse(val entries: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(val rank: Int, val displayName: String? = null, val distanceMeters: Long)
}

@Serializable
internal data class RaceStatusResponse(
    val totalMeters: Long,
    val closesAt: String,
    val contributors: List<Contributor> = emptyList(),
    val myShare: Double? = null
) {
    @Serializable
    data class Contributor(val displayName: String? = null, val distanceMeters: Long, val share: Double)
}

/** 서버 CrewDtos.PostListResponse. 본문은 body이고, 크루런 모집글이면 crewRun(거리·모집 일시·투표 현황)이 함께 온다. */
@Serializable
internal data class PostListResponse(val posts: List<Item> = emptyList()) {
    @Serializable
    data class Item(
        val postId: Long,
        val kind: String,
        val authorName: String? = null,
        val body: String? = null,
        val writtenAt: String,
        val mine: Boolean = false,
        val crewRun: CrewRun? = null
    ) {
        /** 크루런 모집글의 거리(미터). 모집 일시·투표 현황은 앱이 아직 쓰지 않는다. */
        @Serializable
        data class CrewRun(val distanceMeters: Long)
    }
}

/**
 * 응원글(CHEER)은 body만, 크루런 모집글(CREW_RUN)은 body·distanceMeters·모집 일시를 싣는다. 서버 항목 이름은 scheduledAt이고
 * meetAt은 별칭(@JsonAlias)으로 받는다. 서버 모집글에는 제목이 없어 title은 보내지 않는다(투표 마감 voteDeadline은 비우면 모집 일시).
 */
@Serializable
internal data class PostWriteRequest(
    val kind: String,
    val body: String? = null,
    val distanceMeters: Long? = null,
    val meetAt: String? = null
)

@Serializable
internal data class VoteRequest(val answer: String)

/** 서버 CrewDtos.LobbyResponse. 모집 일시는 scheduledAt이다. */
@Serializable
internal data class LobbyResponse(
    val postId: Long,
    val participants: List<Participant> = emptyList(),
    val scheduledAt: String
) {
    @Serializable
    data class Participant(val displayName: String? = null, val ready: Boolean = false)
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
        val displayName: String? = null,
        val elapsedSeconds: Long? = null,
        val averagePaceSecondsPerKm: Int? = null
    )
}
