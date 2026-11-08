package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.MeetupForm
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/**
 * 크루·게시판·대기실 포트를 REST로 구현한다(6.1.14 api.crew). 갈래마다 엔드포인트가 나뉜다(D-36).
 * 목록(크루·게시글)은 [PAGE_SIZE]씩 받아 커서로 이어 받는다.
 */
internal class CrewRepositoryImpl @Inject constructor(private val api: CrewApi) : CrewRepository {

    override suspend fun myCrew(): Outcome<MyCrew> = ApiErrorMapper.call { api.myCrew().toDomain() }

    override suspend fun detail(crewId: Long): Outcome<CrewDetail> = ApiErrorMapper.call {
        api.detail(crewId).toDomain()
    }

    override suspend fun create(form: CrewForm): Outcome<Long> = ApiErrorMapper.call { api.create(form.toRequest()).id }

    override suspend fun changeInfo(crewId: Long, form: CrewForm): Outcome<Unit> =
        ApiErrorMapper.call { api.changeInfo(crewId, form.toRequest()) }

    override suspend fun reissueInviteCode(crewId: Long): Outcome<String> =
        ApiErrorMapper.call { api.reissueInviteCode(crewId).inviteCode }

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

    override suspend fun crewRanking(crewId: Long, period: CrewRanking.Period): Outcome<CrewRanking> =
        ApiErrorMapper.call { api.crewRanking(crewId, period.name).toDomain() }

    override suspend fun race(crewId: Long, weekStart: LocalDate?): Outcome<RaceStatus> =
        ApiErrorMapper.call { api.race(crewId, weekStart?.let(ServerTime::formatDate)).toDomain() }

    override suspend fun posts(crewId: Long, kind: PostList.Kind?, cursor: Long?): Outcome<PostList> =
        ApiErrorMapper.call { api.posts(crewId, kind?.name, cursor, PAGE_SIZE).toDomain() }

    override suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long> =
        ApiErrorMapper.call { api.writePost(crewId, form.toRequest()).id }

    override suspend fun deletePost(postId: Long): Outcome<Unit> = ApiErrorMapper.call { api.deletePost(postId) }

    /** 일정(크루런·대면 모임) 참석 응답(PUT /posts/{id}/attendance, D-99). */
    override suspend fun answerAttendance(postId: Long, answer: AttendanceAnswer): Outcome<Unit> = ApiErrorMapper.call {
        api.answerAttendance(postId, AttendanceRequest(answer.name))
        Unit
    }

    override suspend fun appointManager(crewId: Long, memberId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.appointManager(crewId, memberId)
        Unit
    }

    override suspend fun dismissManager(crewId: Long, memberId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.dismissManager(crewId, memberId)
        Unit
    }

    override suspend fun writeMeetup(crewId: Long, form: MeetupForm): Outcome<Long> = ApiErrorMapper.call {
        val request =
            PostWriteRequest(
                kind = PostList.Kind.MEETUP.name,
                body = form.body,
                scheduledAt = ServerTime.formatWithOffset(form.scheduledAt),
                placeName = form.placeName,
                capacity = form.capacity
            )
        api.writePost(crewId, request).id
    }

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

    override suspend fun quit(postId: Long, distanceMeters: Long?, elapsed: Duration?): Outcome<Unit> =
        ApiErrorMapper.call { api.quit(postId, CrewRunQuitRequest(distanceMeters, elapsed?.seconds)) }

    override suspend fun pause(postId: Long, elapsed: Duration): Outcome<Unit> =
        ApiErrorMapper.call { api.pause(postId, CrewRunPauseRequest(elapsed.seconds)) }

    override suspend fun crewRunResult(postId: Long): Outcome<CrewRunResult> =
        ApiErrorMapper.call { api.result(postId).toDomain() }

    private companion object {
        /** 크루 목록·게시글 한 쪽 크기(서버 PageCursor 1~100). */
        const val PAGE_SIZE = 20
    }
}
