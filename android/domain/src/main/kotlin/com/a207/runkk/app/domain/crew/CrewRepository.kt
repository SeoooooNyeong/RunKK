package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import java.time.Duration
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

    /** 운영진 지정(PUT /crews/{id}/managers/{memberId}, FR-204). 크루장만. 최대 2명(MANAGER_LIMIT). */
    suspend fun appointManager(crewId: Long, memberId: Long): Outcome<Unit>

    /** 운영진 해제(DELETE /crews/{id}/managers/{memberId}, FR-204). 크루장만. */
    suspend fun dismissManager(crewId: Long, memberId: Long): Outcome<Unit>

    suspend fun delete(crewId: Long): Outcome<Unit>

    suspend fun search(keyword: String?, tags: Set<String>, cursor: Long? = null): Outcome<CrewList>

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

    suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long>

    /** 이번 주 공동 목표 정하기(PUT /crews/{id}/goal, FR-138). 운영진 이상. 1km 미만은 GOAL_INVALID. */
    suspend fun setWeeklyGoal(crewId: Long, goal: Distance): Outcome<RaceStatus>

    suspend fun deletePost(postId: Long): Outcome<Unit>

    /** 일정(크루런·대면 모임) 참석 응답(PUT /posts/{id}/attendance, FR-145, D-99). */
    suspend fun answerAttendance(postId: Long, answer: AttendanceAnswer): Outcome<Unit>

    suspend fun enterLobby(postId: Long): Outcome<Lobby>

    suspend fun toggleReady(postId: Long): Outcome<Lobby>

    suspend fun leaveLobby(postId: Long): Outcome<Lobby>

    /**
     * 내 크루런 끝내기(POST /crew-runs/{id}/quit, 합산 방식). [distanceMeters]·[elapsed]는 끝낼 때의 내 진행값이다(모르면 null,
     * 서버가 마지막 중계 값을 쓴다). CrewRunCoordinator.quit이 부른다.
     */
    suspend fun quit(postId: Long, distanceMeters: Long? = null, elapsed: Duration? = null): Outcome<Unit>

    /** 내 일시정지를 알린다(POST /crew-runs/{id}/pause). 재개는 서버가 늘어난 경과의 진행값으로 판단한다. */
    suspend fun pause(postId: Long, elapsed: Duration): Outcome<Unit>

    suspend fun crewRunResult(postId: Long): Outcome<CrewRunResult>

    /**
     * 크루원 얼굴 그림: 대기실·결과 참가자의 faceKey(셀카 아바타)로 부위 그림을 받는다(AccountRepository.faceLayers와 같은 그림).
     * 셀카 얼굴이 아니면 빈 목록, 받지 못하면 실패다. 구현이 없으면 빈 목록이다.
     */
    suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = Outcome.Success(emptyList())

    /**
     * 초대 코드로 크루 요약을 미리 본다(POST /crews/invite-preview, 피그마 F-03c). 가입시키지 않는다. 코드가 없거나 만료면
     * AppError.Rejected(INVALID_CODE), 틀린 코드가 10분에 5번이면 TOO_MANY_ATTEMPTS. 구현이 없으면 실패다.
     */
    suspend fun previewByInviteCode(code: String): Outcome<CrewPreview> =
        Outcome.Failure(AppError.Server(NOT_IMPLEMENTED))

    /** 크루 로고 JPEG(1MB 이하)를 올리고 로고 주소를 받는다(POST /crew-logos, 피그마 F-04). 구현이 없으면 실패다. */
    suspend fun uploadLogo(jpeg: ByteArray): Outcome<String> = Outcome.Failure(AppError.Server(NOT_IMPLEMENTED))

    /** 로고 바이트(GET /crew-logos/{id}). 이 앱 서버의 로고 경로만 읽고 그 밖의 주소는 실패다. 구현이 없으면 실패다. */
    suspend fun logo(url: String): Outcome<ByteArray> = Outcome.Failure(AppError.Server(NOT_IMPLEMENTED))

    private companion object {
        const val NOT_IMPLEMENTED = 501
    }
}
