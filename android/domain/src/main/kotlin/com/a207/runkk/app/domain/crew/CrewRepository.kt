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
