package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.FaceImageSource
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewPreview
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunDetail
import com.a207.runkk.app.domain.crew.CrewRunEdit
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunJoin
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.PastCrewRuns
import com.a207.runkk.app.domain.crew.PastGoals
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 크루·게시판·대기실 포트를 REST로 구현한다(6.1.14 api.crew). 갈래마다 엔드포인트가 나뉜다(D-36).
 * 목록(크루·게시글)은 [PAGE_SIZE]씩 받아 커서로 이어 받는다.
 */
internal class CrewRepositoryImpl @Inject constructor(private val api: CrewApi, private val faces: FaceImageSource) :
    CrewRepository {
    override suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = faces.faceLayers(faceKey)

    override fun cachedFaceLayers(faceKey: String?): List<FaceLayer>? = faces.peek(faceKey)

    override suspend fun myCrew(): Outcome<MyCrew> = ApiErrorMapper.call { api.myCrew().toDomain() }

    override suspend fun detail(crewId: Long): Outcome<CrewDetail> = ApiErrorMapper.call {
        api.detail(crewId).toDomain()
    }

    override suspend fun create(form: CrewForm): Outcome<Long> = ApiErrorMapper.call { api.create(form.toRequest()).id }

    override suspend fun changeInfo(crewId: Long, form: CrewForm): Outcome<Unit> =
        ApiErrorMapper.call { api.changeInfo(crewId, form.toRequest()) }

    override suspend fun transferLeadership(crewId: Long, memberId: Long): Outcome<Unit> =
        ApiErrorMapper.call { api.transferLeadership(crewId, LeaderRequest(memberId)) }

    override suspend fun delete(crewId: Long): Outcome<Unit> = ApiErrorMapper.call { api.delete(crewId) }

    override suspend fun search(keyword: String?, tags: Set<String>, cursor: Long?): Outcome<CrewList> =
        ApiErrorMapper.call { api.search(keyword, tags.sorted(), cursor, PAGE_SIZE).toDomain() }

    override suspend fun apply(crewId: Long): Outcome<JoinResult> = ApiErrorMapper.call {
        api.apply(JoinRequestBody(crewId = crewId)).toDomain()
    }

    override suspend fun applyWithInviteCode(code: String): Outcome<JoinResult> =
        ApiErrorMapper.call { api.apply(JoinRequestBody(inviteCode = code)).toDomain() }

    override suspend fun cancelJoinRequest(requestId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.cancelJoinRequest(requestId)
        Unit
    }

    override suspend fun approveJoin(requestId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.approveJoin(requestId)
    }

    override suspend fun rejectJoin(requestId: Long): Outcome<Unit> = ApiErrorMapper.call { api.rejectJoin(requestId) }

    override suspend fun leave(crewId: Long): Outcome<Unit> = ApiErrorMapper.call { api.leave(crewId) }

    override suspend fun kick(crewId: Long, memberId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.kick(crewId, memberId)
    }

    override suspend fun editCrewProfile(crewId: Long, intro: String): Outcome<Unit> =
        ApiErrorMapper.call { api.editCrewProfile(crewId, CrewProfileRequest(intro)) }

    override suspend fun members(crewId: Long): Outcome<CrewMembers> = ApiErrorMapper.call {
        api.members(crewId).toDomain()
    }

    override suspend fun pendingRequests(crewId: Long): Outcome<PendingRequests> =
        ApiErrorMapper.call { api.pendingRequests(crewId).toDomain() }

    override suspend fun pastGoals(crewId: Long, before: LocalDate?): Outcome<PastGoals> = ApiErrorMapper.call {
        api.goalHistory(crewId, before?.toString(), PAST_GOALS_PAGE_SIZE).toDomain()
    }

    override suspend fun pastCrewRuns(crewId: Long, month: YearMonth, date: LocalDate?): Outcome<PastCrewRuns> =
        ApiErrorMapper.call { api.pastCrewRuns(crewId, month.toString(), date?.toString()).toDomain(month) }

    override suspend fun crewRanking(crewId: Long, period: CrewRanking.Period): Outcome<CrewRanking> =
        ApiErrorMapper.call { api.crewRanking(crewId, period.name).toDomain() }

    override suspend fun race(crewId: Long, weekStart: LocalDate?): Outcome<RaceStatus> =
        ApiErrorMapper.call { api.race(crewId, weekStart?.let(ServerTime::formatDate)).toDomain() }

    override suspend fun posts(crewId: Long, kind: PostList.Kind?, cursor: Long?): Outcome<PostList> =
        ApiErrorMapper.call { api.posts(crewId, kind?.name, cursor, PAGE_SIZE, null, null).toDomain() }

    /** 크루런 탭 목록: 진행 중인 크루런만 예정 일시 오름차순(설계 05-32 4.5). */
    override suspend fun activeCrewRuns(crewId: Long, cursor: Long?): Outcome<PostList> = ApiErrorMapper.call {
        api.posts(crewId, PostList.Kind.CREW_RUN.name, cursor, PAGE_SIZE, SCHEDULED_ASC, true).toDomain()
    }

    override suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long> =
        ApiErrorMapper.call { api.writePost(crewId, form.toRequest()).id }

    override suspend fun deletePost(postId: Long): Outcome<Unit> = ApiErrorMapper.call { api.deletePost(postId) }

    /** 일정(크루런·대면 모임) 참석 응답(PUT /posts/{id}/attendance, D-99). */
    override suspend fun answerAttendance(postId: Long, answer: AttendanceAnswer): Outcome<Unit> = ApiErrorMapper.call {
        api.answerAttendance(postId, AttendanceRequest(answer.name))
        Unit
    }

    /** 참석 취소(DELETE /posts/{id}/attendance, W6). */
    override suspend fun withdrawAttendance(postId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.withdrawAttendance(postId)
        Unit
    }

    override suspend fun crewRun(postId: Long): Outcome<CrewRunDetail> =
        ApiErrorMapper.call { api.crewRun(postId).toDomain() }

    override suspend fun editCrewRun(postId: Long, edit: CrewRunEdit): Outcome<CrewRunDetail> =
        ApiErrorMapper.call { api.editCrewRun(postId, edit.toRequest()).toDomain() }

    override suspend fun setWeeklyGoal(crewId: Long, goal: Distance): Outcome<RaceStatus> =
        ApiErrorMapper.call { api.setGoal(crewId, GoalRequest(goal.meters)).toDomain() }

    override suspend fun enterLobby(postId: Long): Outcome<Lobby> = ApiErrorMapper.call {
        api.enterLobby(postId).toDomain()
    }

    override suspend fun toggleReady(postId: Long): Outcome<Lobby> = ApiErrorMapper.call {
        api.toggleReady(postId).toDomain()
    }

    override suspend fun leaveLobby(postId: Long): Outcome<Lobby> = ApiErrorMapper.call {
        api.leaveLobby(postId).toDomain()
    }

    override suspend fun lobby(postId: Long): Outcome<Lobby> = ApiErrorMapper.call { api.lobby(postId).toDomain() }

    override suspend fun setReady(postId: Long, ready: Boolean): Outcome<Lobby> = ApiErrorMapper.call {
        api.setReady(postId, ReadyRequest(ready)).toDomain()
    }

    override suspend fun startNow(postId: Long): Outcome<Lobby> = ApiErrorMapper.call {
        api.startNow(postId).toDomain()
    }

    override suspend fun join(postId: Long): Outcome<CrewRunJoin> = ApiErrorMapper.call {
        api.join(postId).toDomain()
    }

    override suspend fun quit(postId: Long, distanceMeters: Long?, elapsed: Duration?): Outcome<Unit> =
        ApiErrorMapper.call { api.quit(postId, CrewRunQuitRequest(distanceMeters, elapsed?.seconds)) }

    override suspend fun pause(postId: Long, elapsed: Duration): Outcome<Unit> =
        ApiErrorMapper.call { api.pause(postId, CrewRunPauseRequest(elapsed.seconds)) }

    override suspend fun crewRunResult(postId: Long): Outcome<CrewRunResult> =
        ApiErrorMapper.call { api.result(postId).toDomain() }

    override suspend fun previewByInviteCode(code: String): Outcome<CrewPreview> =
        ApiErrorMapper.call { api.invitePreview(InvitePreviewRequest(code)).toDomain() }

    override suspend fun uploadLogo(jpeg: ByteArray): Outcome<String> = ApiErrorMapper.call {
        api.uploadLogo(MultipartBody.Part.createFormData(LOGO_PART, LOGO_FILE, jpeg.toRequestBody(JPEG))).logoUrl
    }

    /**
     * 이 서버의 로고 경로(/api/v1/crew-logos/{id})만 읽는다. 예전 https 주소는 인증 토큰을 실어 남의 서버로 보내지 않도록 읽지 않는다
     * (화면은 기본 이미지). 로고는 ID마다 바뀌지 않아 최근 몇 장을 메모리에 둔다.
     */
    override suspend fun logo(url: String): Outcome<ByteArray> {
        val logoId = LOGO_PATH.matchEntire(url)?.groupValues?.get(1)?.toLongOrNull()
            ?: return Outcome.Failure(AppError.Rejected(NOT_CREW_LOGO))
        synchronized(logos) { logos[logoId] }?.let { return Outcome.Success(it) }
        val read = ApiErrorMapper.call { api.logo(logoId).use { body -> withContext(Dispatchers.IO) { body.bytes() } } }
        if (read is Outcome.Success) synchronized(logos) { logos[logoId] = read.value }
        return read
    }

    private val logos = object : LinkedHashMap<Long, ByteArray>(LOGO_CACHE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > LOGO_CACHE
    }

    private companion object {
        /** 크루 목록·게시글 한 쪽 크기(서버 PageCursor 1~100). */
        const val PAGE_SIZE = 20

        /** 크루런 탭 날짜순(서버 crew.PostOrder.SCHEDULED_ASC). */
        const val SCHEDULED_ASC = "SCHEDULED_ASC"

        const val LOGO_PART = "logo"
        const val LOGO_FILE = "logo.jpg"
        const val NOT_CREW_LOGO = "NOT_CREW_LOGO"
        const val LOGO_CACHE = 32
        const val LOAD_FACTOR = 0.75f
        val JPEG = "image/jpeg".toMediaType()
        val LOGO_PATH = Regex("^/api/v1/crew-logos/(\\d{1,18})$")

        /** 지난 공동 목표 한 쪽 크기(서버 기본과 같다). */
        const val PAST_GOALS_PAGE_SIZE = 20
    }
}
