package com.a207.runkk.app.api.battle

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.FriendInvite
import com.a207.runkk.app.domain.battle.FriendInviteView
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.battle.RecordBattleStart
import com.a207.runkk.app.domain.battle.RecordTrack
import javax.inject.Inject
import retrofit2.HttpException
import retrofit2.Response

/** 대결 포트를 REST로 구현한다(6.1.14 api.battle). 확인 단계는 화면이 하고 확인된 요청만 보낸다. */
internal class BattleRepositoryImpl
@Inject
constructor(private val api: BattleApi) : BattleRepository {
    override suspend fun matchStatus(): Outcome<MatchAcceptance> = ApiErrorMapper.call { api.matchStatus().toDomain() }

    override suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance> =
        ApiErrorMapper.call { api.requestMatch(MatchRequest(distance.meters)).toDomain() }

    override suspend fun cancel(): Outcome<MatchAcceptance> = ApiErrorMapper.call { api.cancel().toDomain() }

    override suspend fun selfCandidates(): Outcome<RecordBattleCandidates> =
        ApiErrorMapper.call { api.selfCandidates().toDomain() }

    override suspend fun celebrityCandidates(distance: Distance?): Outcome<RecordBattleCandidates> =
        ApiErrorMapper.call { api.celebrityCandidates(distance?.meters).toDomain() }

    override suspend fun recordTracks(kind: RecordBattleKind): Outcome<List<RecordTrack>> =
        ApiErrorMapper.call { api.recordTracks(kind.name).tracks.map { it.toDomain(kind) } }

    override suspend fun startSelfBattle(distance: Distance): Outcome<RecordBattleStart> =
        ApiErrorMapper.call { api.startSelfBattle(SelfBattleRequest(distance.meters)).toDomain() }

    override suspend fun startCelebrityBattle(celebrityId: Long): Outcome<RecordBattleStart> =
        ApiErrorMapper.call { api.startCelebrityBattle(CelebrityBattleRequest(celebrityId)).toDomain() }

    override suspend fun markStarted(battleId: Long): Outcome<Unit> = ApiErrorMapper.call { api.markStarted(battleId) }

    override suspend fun forfeit(battleId: Long): Outcome<Unit> = ApiErrorMapper.call { api.forfeit(battleId) }

    override suspend fun cancel(battleId: Long): Outcome<Unit> = ApiErrorMapper.call { api.cancel(battleId) }

    /** 204면 Success(null). 그 밖의 오류 응답은 AppError로 바꾼다. */
    override suspend fun active(): Outcome<ActiveBattle?> = ApiErrorMapper.call {
        val reply = api.active()
        if (!reply.isSuccessful) throw HttpException(reply)
        reply.body()?.toDomain()
    }

    override suspend fun result(battleId: Long): Outcome<BattleResult> = ApiErrorMapper.call {
        api.result(battleId).toDomain()
    }

    override suspend fun history(filter: BattleHistory.Type?, cursor: Long?): Outcome<BattleHistory> =
        ApiErrorMapper.call { api.history(filter?.name, cursor, HISTORY_PAGE_SIZE).toDomain() }

    override suspend fun createFriendInvite(distance: Distance): Outcome<FriendInvite> =
        ApiErrorMapper.call { api.createFriendInvite(MatchRequest(distance.meters)).toDomain() }

    /** 204면 Success(null). */
    override suspend fun myFriendInvite(): Outcome<FriendInvite?> = ApiErrorMapper.call {
        val reply = api.myFriendInvite()
        if (!reply.isSuccessful) throw HttpException(reply)
        reply.body()?.toDomain()
    }

    override suspend fun cancelFriendInvite(inviteId: Long): Outcome<Unit> =
        ApiErrorMapper.call { api.cancelFriendInvite(inviteId).orThrow() }

    override suspend fun friendInvite(token: String): Outcome<FriendInviteView> =
        ApiErrorMapper.call { api.friendInvite(token).toDomain() }

    override suspend fun acceptFriendInvite(token: String): Outcome<Long> =
        ApiErrorMapper.call { api.acceptFriendInvite(token).battleId }

    override suspend fun declineFriendInvite(token: String): Outcome<Unit> =
        ApiErrorMapper.call { api.declineFriendInvite(token).orThrow() }

    /** 본문 없는 응답(204)의 성공 여부. 오류 응답은 ApiErrorMapper가 본문 code로 바꾸도록 예외로 던진다. */
    private fun Response<Unit>.orThrow() {
        if (!isSuccessful) throw HttpException(this)
    }

    private companion object {
        /** 대결 이력 한 쪽 크기(서버 PageCursor 1~100). */
        const val HISTORY_PAGE_SIZE = 20
    }
}
