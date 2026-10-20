package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.crew.VoteAnswer
import java.time.LocalDate
import javax.inject.Inject

/** 크루·게시판·대기실 포트를 REST로 구현한다(6.1.14 api.crew). 갈래마다 엔드포인트가 나뉜다(D-36). */
internal class CrewRepositoryImpl
@Inject
constructor(private val api: CrewApi) : CrewRepository {
    override suspend fun create(form: CrewForm): Outcome<Long> = ApiErrorMapper.call { api.create(form.toRequest()).id }

    override suspend fun changeInfo(crewId: Long, form: CrewForm): Outcome<Unit> =
        ApiErrorMapper.call { api.changeInfo(crewId, form.toRequest()) }

    override suspend fun reissueInviteCode(crewId: Long): Outcome<String> =
        ApiErrorMapper.call { api.reissueInviteCode(crewId).inviteCode }

    override suspend fun transferLeadership(crewId: Long, memberId: Long): Outcome<Unit> =
        ApiErrorMapper.call { api.transferLeadership(crewId, LeaderRequest(memberId)) }

    override suspend fun delete(crewId: Long): Outcome<Unit> = ApiErrorMapper.call { api.delete(crewId) }

    override suspend fun search(keyword: String?, tags: Set<String>): Outcome<CrewList> =
        ApiErrorMapper.call { api.search(keyword, tags.sorted()).toDomain() }

    override suspend fun recommend(): Outcome<CrewList> = ApiErrorMapper.call { api.recommend().toDomain() }

    override suspend fun apply(crewId: Long): Outcome<Unit> = ApiErrorMapper.call { api.apply(JoinRequestBody(crewId = crewId)) }

    override suspend fun applyWithInviteCode(code: String): Outcome<Unit> =
        ApiErrorMapper.call { api.apply(JoinRequestBody(inviteCode = code)) }

    override suspend fun cancelJoinRequest(requestId: Long): Outcome<Unit> =
        ApiErrorMapper.call { api.cancelJoinRequest(requestId) }

    override suspend fun approveJoin(requestId: Long): Outcome<Unit> = ApiErrorMapper.call { api.approveJoin(requestId) }

    override suspend fun rejectJoin(requestId: Long): Outcome<Unit> = ApiErrorMapper.call { api.rejectJoin(requestId) }

    override suspend fun leave(crewId: Long): Outcome<Unit> = ApiErrorMapper.call { api.leave(crewId) }

    override suspend fun kick(crewId: Long, memberId: Long): Outcome<Unit> = ApiErrorMapper.call { api.kick(crewId, memberId) }

    override suspend fun editCrewProfile(crewId: Long, intro: String): Outcome<Unit> =
        ApiErrorMapper.call { api.editCrewProfile(crewId, CrewProfileRequest(intro)) }

    override suspend fun members(crewId: Long): Outcome<CrewMembers> = ApiErrorMapper.call { api.members(crewId).toDomain() }

    override suspend fun pendingRequests(crewId: Long): Outcome<PendingRequests> =
        ApiErrorMapper.call { api.pendingRequests(crewId).toDomain() }

    override suspend fun crewRanking(crewId: Long, period: CrewRanking.Period): Outcome<CrewRanking> =
        ApiErrorMapper.call { api.crewRanking(crewId, period.name).toDomain() }

    override suspend fun race(crewId: Long, weekStart: LocalDate?): Outcome<RaceStatus> =
        ApiErrorMapper.call { api.race(crewId, weekStart?.let(ServerTime::formatDate)).toDomain() }

    override suspend fun posts(crewId: Long, kind: PostList.Kind?): Outcome<PostList> =
        ApiErrorMapper.call { api.posts(crewId, kind?.name).toDomain() }

    override suspend fun writeCheer(crewId: Long, body: String): Outcome<Long> =
        ApiErrorMapper.call { api.writePost(crewId, PostWriteRequest(kind = PostList.Kind.CHEER.name, body = body)).id }

    override suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long> =
        ApiErrorMapper.call { api.writePost(crewId, form.toRequest()).id }

    override suspend fun deletePost(postId: Long): Outcome<Unit> = ApiErrorMapper.call { api.deletePost(postId) }

    override suspend fun vote(postId: Long, answer: VoteAnswer): Outcome<Unit> =
        ApiErrorMapper.call { api.vote(postId, VoteRequest(answer.name)) }

    override suspend fun enterLobby(postId: Long): Outcome<Lobby> = ApiErrorMapper.call { api.enterLobby(postId).toDomain() }

    override suspend fun toggleReady(postId: Long): Outcome<Lobby> = ApiErrorMapper.call { api.toggleReady(postId).toDomain() }

    override suspend fun leaveLobby(postId: Long): Outcome<Lobby> = ApiErrorMapper.call { api.leaveLobby(postId).toDomain() }

    override suspend fun quit(postId: Long): Outcome<Unit> = ApiErrorMapper.call { api.quit(postId) }

    override suspend fun crewRunResult(postId: Long): Outcome<CrewRunResult> =
        ApiErrorMapper.call { api.result(postId).toDomain() }
}
