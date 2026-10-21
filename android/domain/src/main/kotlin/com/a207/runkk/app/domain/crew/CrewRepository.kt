package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/**
 * 크루·게시판·크루런 대기실 REST 포트(5.7.1 /crews/…, /join-requests/…, /posts/…, /crew-runs/…). 구현 api.crew.
 * 갈래마다 연산을 나눴다(D-36: approveJoin / rejectJoin, enterLobby / toggleReady / leaveLobby).
 * 확인 단계는 화면이 하고 확인된 요청만 보낸다. 거부는 AppError.Rejected(사유 코드)로 온다.
 */
interface CrewRepository {
    suspend fun create(form: CrewForm): Outcome<Long>

    suspend fun changeInfo(crewId: Long, form: CrewForm): Outcome<Unit>

    suspend fun reissueInviteCode(crewId: Long): Outcome<String>

    suspend fun transferLeadership(crewId: Long, memberId: Long): Outcome<Unit>

    suspend fun delete(crewId: Long): Outcome<Unit>

    suspend fun search(keyword: String?, tags: Set<String>): Outcome<CrewList>

    suspend fun recommend(): Outcome<CrewList>

    suspend fun apply(crewId: Long): Outcome<Unit>

    suspend fun applyWithInviteCode(code: String): Outcome<Unit>

    suspend fun cancelJoinRequest(requestId: Long): Outcome<Unit>

    suspend fun approveJoin(requestId: Long): Outcome<Unit>

    suspend fun rejectJoin(requestId: Long): Outcome<Unit>

    suspend fun leave(crewId: Long): Outcome<Unit>

    suspend fun kick(crewId: Long, memberId: Long): Outcome<Unit>

    suspend fun editCrewProfile(crewId: Long, intro: String): Outcome<Unit>

    suspend fun members(crewId: Long): Outcome<CrewMembers>

    suspend fun pendingRequests(crewId: Long): Outcome<PendingRequests>

    suspend fun crewRanking(crewId: Long, period: CrewRanking.Period): Outcome<CrewRanking>

    suspend fun race(crewId: Long, weekStart: LocalDate?): Outcome<RaceStatus>

    suspend fun posts(crewId: Long, kind: PostList.Kind?): Outcome<PostList>

    suspend fun writeCheer(crewId: Long, body: String): Outcome<Long>

    suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long>

    suspend fun deletePost(postId: Long): Outcome<Unit>

    suspend fun vote(postId: Long, answer: VoteAnswer): Outcome<Unit>

    suspend fun enterLobby(postId: Long): Outcome<Lobby>

    suspend fun toggleReady(postId: Long): Outcome<Lobby>

    suspend fun leaveLobby(postId: Long): Outcome<Lobby>

    /** 크루런 중간 그만두기(POST /crew-runs/{id}/quit). CrewRunCoordinator.quit이 부른다. */
    suspend fun quit(postId: Long): Outcome<Unit>

    suspend fun crewRunResult(postId: Long): Outcome<CrewRunResult>
}

/** 크루 공개 범위(서버 crew.CrewVisibility). */
enum class CrewVisibility {
    PUBLIC,
    PRIVATE
}

/** 가입 방식(서버 crew.JoinPolicy). */
enum class JoinPolicy {
    APPROVAL,
    FREE
}

/** 크루런 참가 투표 답(서버 crew.VoteAnswer). */
enum class VoteAnswer {
    GOING,
    NOT_GOING
}

/** 크루 만들기·정보 바꾸기 입력(FR-110, FR-112). */
data class CrewForm(
    val name: String,
    val intro: String?,
    val tags: Set<String>,
    val visibility: CrewVisibility,
    val joinPolicy: JoinPolicy
)

/** 크루런 모집글 입력(FR-145). */
data class CrewRunForm(val title: String, val body: String?, val distance: Distance, val meetAt: Instant)

/** 크루 목록·추천(서버 crew.CrewListView·RecommendationView). */
data class CrewList(val crews: List<Item>) {
    data class Item(
        val crewId: Long,
        val name: String,
        val intro: String?,
        val tags: Set<String>,
        val leaderName: String,
        val headcount: Int,
        val weeklyDistance: Distance,
        val joinPolicy: JoinPolicy
    )
}

/** 크루원 목록(서버 crew.CrewMembersView + Runimal 유형, D-46·D-52). 비공개 크루원은 표시명·역할·소개만 온다. */
data class CrewMembers(val members: List<Member>) {
    data class Member(
        val memberId: Long,
        val displayName: String,
        val role: String,
        val crewIntro: String?,
        val tier: String?,
        val runimalType: String?
    )
}

/** 대기 중 가입 신청(서버 crew.PendingRequestsView). */
data class PendingRequests(val requests: List<Request>) {
    data class Request(val requestId: Long, val memberId: Long, val displayName: String, val requestedAt: Instant)
}

/** 크루 안 랭킹(서버 crew.CrewRankingView, FR-137). */
data class CrewRanking(val entries: List<Entry>) {
    enum class Period {
        WEEKLY,
        MONTHLY
    }

    data class Entry(val rank: Int, val displayName: String, val distance: Distance)
}

/** 주간 레이스(서버 crew.RaceStatusView, FR-140). */
data class RaceStatus(
    val total: Distance,
    val closesAt: Instant,
    val contributors: List<Contributor>,
    val myShare: Double?
) {
    data class Contributor(val displayName: String, val distance: Distance, val share: Double)
}

/** 크루 게시판(서버 crew.PostListView, FR-131·FR-133). */
data class PostList(val posts: List<Item>) {
    enum class Kind {
        CHEER,
        CREW_RUN
    }

    data class Item(
        val postId: Long,
        val kind: Kind,
        val authorName: String,
        val summary: String,
        val writtenAt: Instant,
        val mine: Boolean,
        /** 크루런 모집글의 거리. 응원글이거나 서버가 주지 않으면 null이다. */
        val distance: Distance? = null
    )
}

/** 크루런 대기실(서버 crew.LobbyView, FR-147). */
data class Lobby(val postId: Long, val participants: List<Participant>, val meetAt: Instant) {
    data class Participant(val displayName: String, val ready: Boolean)
}

/** 크루런 결과(서버 crew.CrewRunResultView, FR-191). */
data class CrewRunResult(
    val postId: Long,
    val finishers: List<Finisher>,
    val firstCrewRun: Boolean,
    val raceContribution: Distance
) {
    /** rank가 null이면 미완주. */
    data class Finisher(
        val rank: Int?,
        val displayName: String,
        val elapsedSeconds: Long?,
        val averagePaceSecondsPerKm: Int?
    )
}
