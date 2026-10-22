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

/** POST /crews/join·DELETE /join-requests/{id} 결과(서버 CrewDtos.JoinResponse). status는 JOINED·PENDING·CANCELLED. */
@Serializable
internal data class JoinResponse(val status: String, val crewId: Long, val requestId: Long? = null)

@Serializable
internal data class CrewProfileRequest(val intro: String)

/** GET /crews/{id}, GET /crews/me의 crew(서버 CrewDtos.CrewDetailResponse). 초대 코드는 크루장에게만 온다. */
@Serializable
internal data class CrewDetailResponse(
    val crewId: Long,
    val name: String,
    val intro: String? = null,
    val tags: Set<String> = emptySet(),
    val visibility: String,
    val joinPolicy: String,
    val logoUrl: String? = null,
    val leaderId: Long,
    val leaderName: String? = null,
    val headcount: Int,
    val weeklyDistanceMeters: Long = 0,
    val myRole: String? = null,
    val inviteCode: String? = null,
    val inviteCodeExpiresAt: String? = null
)

/** GET /crews/me(서버 CrewDtos.MyCrewResponse). 소속 크루가 없으면 crew가 null이다. */
@Serializable
internal data class MyCrewResponse(
    val crew: CrewDetailResponse? = null,
    val pendingRequests: List<PendingJoin> = emptyList()
) {
    @Serializable
    data class PendingJoin(val requestId: Long, val crewId: Long, val crewName: String, val requestedAt: String)
}

/** GET /crews(서버 CrewDtos.CrewListResponse). nextCursor는 size를 보냈을 때 다음 쪽 커서이고 끝이면 null이다. */
@Serializable
internal data class CrewListResponse(val crews: List<Item> = emptyList(), val nextCursor: Long? = null) {
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
        val runimalType: String? = null,
        val me: Boolean = false
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
    data class Contributor(
        val displayName: String? = null,
        val distanceMeters: Long,
        val share: Double,
        val memberId: Long? = null,
        val me: Boolean = false
    )
}

/**
 * 서버 CrewDtos.PostListResponse. 본문은 body이고, 크루런 모집글이면 crewRun(거리·모집 일시·투표 현황)이 함께 온다.
 * nextCursor는 size를 보냈을 때 다음 쪽 커서이고 끝이면 null이다.
 */
@Serializable
internal data class PostListResponse(val posts: List<Item> = emptyList(), val nextCursor: Long? = null) {
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
        /** 크루런 모집글의 크루런 정보(서버 PostListResponse.CrewRun). myAnswer는 GOING·NOT_GOING, 답하지 않았으면 null. */
        @Serializable
        data class CrewRun(
            val distanceMeters: Long,
            val scheduledAt: String,
            val voteDeadline: String? = null,
            val status: String? = null,
            val goingCount: Int = 0,
            val myAnswer: String? = null,
            val finisherCount: Int = 0,
            val raceContributionMeters: Long? = null
        )
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
