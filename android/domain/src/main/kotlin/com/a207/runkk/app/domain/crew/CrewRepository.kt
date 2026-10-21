package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/**
 * 크루·게시판·크루런 대기실 REST 포트(5.7.1 /crews/…, /join-requests/…, /posts/…, /crew-runs/…). 구현 api.crew.
 * 갈래마다 연산을 나눴다(D-36: approveJoin / rejectJoin, enterLobby / toggleReady / leaveLobby).
 * 확인 단계는 화면이 하고 확인된 요청만 보낸다. 거부는 AppError.Rejected(사유 코드)로 온다.
 * 목록(search·posts)은 cursor로 이어 받는다: null이면 첫 쪽이고, 결과의 nextCursor가 null이면 끝이다.
 */
interface CrewRepository {
    /** 본인 소속 크루와 대기 중인 본인 가입 신청(GET /crews/me, 크루 탭 재진입). */
    suspend fun myCrew(): Outcome<MyCrew>

    /** 크루 한 곳(GET /crews/{id}). 비공개 크루를 크루 밖에서 보면 CREW_NOT_FOUND. 초대 코드는 크루장에게만 온다. */
    suspend fun detail(crewId: Long): Outcome<CrewDetail>

    suspend fun create(form: CrewForm): Outcome<Long>

    suspend fun changeInfo(crewId: Long, form: CrewForm): Outcome<Unit>

    suspend fun reissueInviteCode(crewId: Long): Outcome<String>

    suspend fun transferLeadership(crewId: Long, memberId: Long): Outcome<Unit>

    suspend fun delete(crewId: Long): Outcome<Unit>

    suspend fun search(keyword: String?, tags: Set<String>, cursor: Long? = null): Outcome<CrewList>

    suspend fun recommend(): Outcome<CrewList>

    /** 크루 ID로 가입(자유 가입은 JOINED, 승인제는 PENDING + 신청 ID). */
    suspend fun apply(crewId: Long): Outcome<JoinResult>

    /** 초대 코드 가입(승인 없이 JOINED, FR-122). */
    suspend fun applyWithInviteCode(code: String): Outcome<JoinResult>

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

    suspend fun posts(crewId: Long, kind: PostList.Kind?, cursor: Long? = null): Outcome<PostList>

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

/** 크루 안 역할(서버 crew.CrewRole). */
enum class CrewRole {
    LEADER,
    MEMBER
}

/** 크루런 상태(서버 crew.CrewRunStatus). */
enum class CrewRunStatus {
    VOTING,
    LOBBY_OPEN,
    RUNNING,
    FINISHED,
    CANCELLED
}

/**
 * 크루 한 곳(서버 crew.CrewDetailView). [myRole]은 크루 밖이면 null이고, [inviteCode]·[inviteCodeExpiresAt]은 크루장에게만 온다.
 */
data class CrewDetail(
    val crewId: Long,
    val name: String,
    val intro: String?,
    val tags: Set<String>,
    val visibility: CrewVisibility,
    val joinPolicy: JoinPolicy,
    val logoUrl: String?,
    val leaderId: Long,
    val leaderName: String,
    val headcount: Int,
    val weeklyDistance: Distance,
    val myRole: CrewRole?,
    val inviteCode: String?,
    val inviteCodeExpiresAt: Instant?
) {
    val isLeader: Boolean get() = myRole == CrewRole.LEADER
}

/** 본인 크루 현황(서버 crew.MyCrewView): 소속 크루(없으면 null)와 대기 중인 본인 가입 신청. */
data class MyCrew(val crew: CrewDetail?, val pendingRequests: List<PendingJoin>) {
    /** 대기 중인 본인 가입 신청 한 건(취소는 CrewRepository.cancelJoinRequest). */
    data class PendingJoin(val requestId: Long, val crewId: Long, val crewName: String, val requestedAt: Instant)
}

/** 가입 신청 결과(서버 crew.JoinResult). [requestId]는 승인제 신청(PENDING)에만 있다. */
data class JoinResult(val status: Status, val crewId: Long, val requestId: Long?) {
    enum class Status {
        JOINED,
        PENDING,
        CANCELLED
    }
}

/** 크루 목록·추천(서버 crew.CrewListView·RecommendationView). [nextCursor]가 null이면 마지막 쪽이다. */
data class CrewList(val crews: List<Item>, val nextCursor: Long? = null) {
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

/**
 * 크루원 목록(서버 crew.CrewMembersView + Runimal 유형, D-46·D-52). 비공개 크루원은 표시명·역할·소개만 온다.
 * [Member.me]는 열람자 본인 줄이다.
 */
data class CrewMembers(val members: List<Member>) {
    data class Member(
        val memberId: Long,
        val displayName: String,
        val role: String,
        val crewIntro: String?,
        val tier: String?,
        val runimalType: String?,
        val me: Boolean = false
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
    /** [memberId]는 탈퇴했으면 null, [me]는 열람자 본인이다. */
    data class Contributor(
        val displayName: String,
        val distance: Distance,
        val share: Double,
        val memberId: Long? = null,
        val me: Boolean = false
    )
}

/** 크루 게시판(서버 crew.PostListView, FR-131·FR-133). [nextCursor]가 null이면 마지막 쪽이다. */
data class PostList(val posts: List<Item>, val nextCursor: Long? = null) {
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
        /** 크루런 모집글의 크루런 정보. 응원글이면 null이다. */
        val crewRun: CrewRunInfo? = null
    )

    /**
     * 모집글의 크루런 정보(FR-133): 거리·모집 일시·투표 마감·상태·참여 인원·내 응답·완주 인원·크루 반영 거리(지난 크루런).
     * [myAnswer]는 아직 답하지 않았으면 null, [status]는 앱이 모르는 상태면 null이다.
     */
    data class CrewRunInfo(
        val distance: Distance,
        val scheduledAt: Instant,
        val voteDeadline: Instant?,
        val status: CrewRunStatus?,
        val goingCount: Int,
        val myAnswer: VoteAnswer?,
        val finisherCount: Int,
        val raceContribution: Distance?
    )
}

/** 크루런 대기실(서버 crew.LobbyView, FR-147). [distance]는 크루런 거리다. */
data class Lobby(val postId: Long, val participants: List<Participant>, val meetAt: Instant, val distance: Distance) {
    /** [me]는 열람자 본인 줄이다. */
    data class Participant(
        val displayName: String,
        val ready: Boolean,
        val memberId: Long? = null,
        val me: Boolean = false
    )
}

/** 크루런 결과(서버 crew.CrewRunResultView, FR-191). */
data class CrewRunResult(
    val postId: Long,
    val finishers: List<Finisher>,
    val firstCrewRun: Boolean,
    val raceContribution: Distance
) {
    /** rank가 null이면 미완주. [quit]은 중간에 그만둔 사람, [me]는 열람자 본인, [memberId]는 탈퇴했으면 null. */
    data class Finisher(
        val rank: Int?,
        val displayName: String,
        val elapsedSeconds: Long?,
        val averagePaceSecondsPerKm: Int?,
        val quit: Boolean = false,
        val memberId: Long? = null,
        val me: Boolean = false
    )
}
