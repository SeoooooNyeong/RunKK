package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
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

    suspend fun writeCheer(crewId: Long, body: String): Outcome<Long>

    suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long>

    /** 공지 쓰기(FR-205). 운영진 이상. 고정 공지는 3개까지(PIN_LIMIT). */
    suspend fun writeNotice(crewId: Long, body: String, isPinned: Boolean): Outcome<Long>

    /** 공지 고정·해제(PUT /posts/{id}/pin, FR-205). */
    suspend fun pin(postId: Long, isPinned: Boolean): Outcome<Unit>

    /** 투표 쓰기(FR-206). */
    suspend fun writePoll(crewId: Long, form: PollForm): Outcome<Long>

    /** 투표 응답(FR-206). 마감 뒤에는 POLL_CLOSED. */
    suspend fun respondPoll(postId: Long, optionNo: Int): Outcome<Unit>

    suspend fun pollResult(postId: Long): Outcome<PollResult>

    /** 대면 모임 일정 쓰기(FR-207). 작성자는 GOING으로 들어간다. */
    suspend fun writeMeetup(crewId: Long, form: MeetupForm): Outcome<Long>

    /** 게시판 글 쓰기(FR-211). [photoUris]는 기기 사진 주소이고 api.crew가 줄여서 보낸다(최대 5장, D-100). */
    suspend fun writeBoard(crewId: Long, body: String, photoUris: List<String>): Outcome<Long>

    /**
     * 게시판·앨범 사진 한 장의 JPEG 바이트(GET /crew-photos/{id}, FR-212, D-100). 인증된 연결로 읽고, 크루 밖·숨김·삭제면 거부가 온다.
     * 사진 주소 대신 ID로 받아 인증 헤더가 서버 밖 주소로 나가지 않게 한다.
     */
    suspend fun photo(photoId: Long): Outcome<ByteArray>

    /** 크루 앨범(GET /crews/{id}/album, FR-212). */
    suspend fun album(crewId: Long, cursor: Long? = null): Outcome<Album>

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
}
