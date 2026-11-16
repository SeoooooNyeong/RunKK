package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

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

    suspend fun transferLeadership(crewId: Long, memberId: Long): Outcome<Unit>

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

    /**
     * 지난 공동 목표 한 쪽(GET /crews/{id}/goals/history, 피그마 F-21, contract-W7 4장). [before]는 앞 쪽의 nextBefore(첫 쪽은 null).
     * 최신 주부터 온다.
     */
    suspend fun pastGoals(crewId: Long, before: LocalDate? = null): Outcome<PastGoals>

    /**
     * 지난 크루런 한 달(GET /crews/{id}/crew-runs/past, 피그마 F-20, contract-W6). [date]를 주면 runs가 그날 것만이다.
     */
    suspend fun pastCrewRuns(crewId: Long, month: YearMonth, date: LocalDate? = null): Outcome<PastCrewRuns>

    suspend fun crewRanking(crewId: Long, period: CrewRanking.Period): Outcome<CrewRanking>

    suspend fun race(crewId: Long, weekStart: LocalDate?): Outcome<RaceStatus>

    suspend fun posts(crewId: Long, kind: PostList.Kind?, cursor: Long? = null): Outcome<PostList>

    suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long>

    /** 이번 주 공동 목표 정하기(PUT /crews/{id}/goal, FR-138). 운영진 이상. 1km 미만은 GOAL_INVALID. */
    suspend fun setWeeklyGoal(crewId: Long, goal: Distance): Outcome<RaceStatus>

    suspend fun deletePost(postId: Long): Outcome<Unit>

    /** 일정(크루런·대면 모임) 참석 응답(PUT /posts/{id}/attendance, FR-145, D-99). 앱은 참석하기에 GOING만 보낸다(W6). */
    suspend fun answerAttendance(postId: Long, answer: AttendanceAnswer): Outcome<Unit>

    /** 참석 취소(DELETE /posts/{id}/attendance, 피그마 F-13b): 크루런 참석 응답을 거둔다. 응답이 없어도 성공이다. */
    suspend fun withdrawAttendance(postId: Long): Outcome<Unit>

    /** 크루런 일정 상세(GET /crew-runs/{id}, 피그마 F-13·F-11d). 크루원만. */
    suspend fun crewRun(postId: Long): Outcome<CrewRunDetail>

    /** 모집글 수정(PATCH /crew-runs/{id}, 피그마 F-13d). 작성자만, 투표 중이고 일시 전. 바뀐 상세를 돌려준다. */
    suspend fun editCrewRun(postId: Long, edit: CrewRunEdit): Outcome<CrewRunDetail>

    suspend fun enterLobby(postId: Long): Outcome<Lobby>

    suspend fun toggleReady(postId: Long): Outcome<Lobby>

    suspend fun leaveLobby(postId: Long): Outcome<Lobby>

    /**
     * 대기실 다시 읽기(GET /crew-runs/{id}/lobby, 설계 05-32 [임시:D-성-879]). 준비·참여를 바꾸지 않는다(enter를 다시 부르면 READY가
     * 풀린다). 출발·종료 뒤에도 현황을 준다. 구현이 없으면 실패다.
     */
    suspend fun lobby(postId: Long): Outcome<Lobby> = Outcome.Failure(AppError.Server(NOT_IMPLEMENTED))

    /** 멱등 준비(PUT /crew-runs/{id}/lobby/ready). 같은 값을 다시 보내도 바뀌지 않는다. 카운트다운 중 false는 출발 후보에서만 빠진다. */
    suspend fun setReady(postId: Long, ready: Boolean): Outcome<Lobby> =
        Outcome.Failure(AppError.Server(NOT_IMPLEMENTED))

    /** 모집자 「지금 시작」(POST /crew-runs/{id}/start). 준비자 2명 이상일 때 최초 예약, 다시 부르면 같은 예약을 돌려준다. */
    suspend fun startNow(postId: Long): Outcome<Lobby> = Outcome.Failure(AppError.Server(NOT_IMPLEMENTED))

    /** 달리는 크루런 중간 참여(POST /crew-runs/{id}/join). 다시 불러도 같은 joinedAt·measurementRecordId다. */
    suspend fun join(postId: Long): Outcome<CrewRunJoin> = Outcome.Failure(AppError.Server(NOT_IMPLEMENTED))

    /**
     * 크루런 탭 목록(GET /crews/{id}/posts?kind=CREW_RUN&order=SCHEDULED_ASC&activeOnly=true, 설계 05-32 4.5): 투표 중·대기실·진행 중인
     * 크루런을 예정 일시, 같은 일시는 postId 오름차순으로 준다. [cursor]는 앞 쪽 마지막 글 ID다. 구현이 없으면 기존 최신순 목록이다.
     */
    suspend fun activeCrewRuns(crewId: Long, cursor: Long? = null): Outcome<PostList> =
        posts(crewId, PostList.Kind.CREW_RUN, cursor)

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

    /** 기다리지 않고 바로 그릴 수 있는 크루원 얼굴 그림(앞서 받아 둔 것, AccountRepository.cachedFaceLayers와 같다). 없으면 null. */
    fun cachedFaceLayers(faceKey: String?): List<FaceLayer>? = null

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
