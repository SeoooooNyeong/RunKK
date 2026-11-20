package com.a207.runkk.app.api.battle

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.ScreenCopyStore
import com.a207.runkk.app.api.core.SelfMember
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import retrofit2.HttpException
import retrofit2.Response

/**
 * 대결 포트를 REST로 구현한다(6.1.14 api.battle). 확인 단계는 화면이 하고 확인된 요청만 보낸다.
 *
 * 화면 첫 응답 사본(S15P31A207-345 2차, [copies]): 셀럽 후보(전체 거리)와 대결 이력 첫 쪽은 받을 때마다 기기에 남기고(내 계정 칸, 셀럽
 * 후보는 내 최고 기록을 뺀 공용 칸에도) 화면이 먼저 그릴 수 있게 cached…로 준다. 대결을 만드는 요청은 남기지 않는다.
 */
internal class BattleRepositoryImpl
@Inject
constructor(
    private val api: BattleApi,
    private val self: SelfMember? = null,
    private val copies: ScreenCopyStore? = null
) : BattleRepository {
    override suspend fun matchStatus(): Outcome<MatchAcceptance> = ApiErrorMapper.call { api.matchStatus().toDomain() }

    override suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance> =
        ApiErrorMapper.call { api.requestMatch(MatchRequest(distance.meters)).toDomain() }

    override suspend fun cancel(): Outcome<MatchAcceptance> = ApiErrorMapper.call { api.cancel().toDomain() }

    override suspend fun selfCandidates(): Outcome<RecordBattleCandidates> =
        ApiErrorMapper.call { api.selfCandidates().toDomain() }

    override suspend fun celebrityCandidates(distance: Distance?): Outcome<RecordBattleCandidates> {
        val member = self?.memberId()
        return ApiErrorMapper.call {
            api.celebrityCandidates(distance?.meters).also { response ->
                if (distance != null) return@also
                keep(member, CELEB, CandidatesResponse.serializer(), response)
                // 공용 칸: 셀럽 목록·기록은 계정과 상관없다(내 최고 기록만 뺀다).
                val shared = response.copy(candidates = response.candidates.map { it.copy(myBestSeconds = null) })
                keep(null, CELEB, CandidatesResponse.serializer(), shared, isShared = true)
            }.toDomain()
        }
    }

    override suspend fun cachedCelebrityCandidates(distance: Distance?): RecordBattleCandidates? {
        if (distance != null) return null
        return withContext(Dispatchers.IO) {
            val store = copies ?: return@withContext null
            val member = self?.memberId() ?: return@withContext null
            val serializer = CandidatesResponse.serializer()
            (store.read(member, CELEB, serializer) ?: store.read(null, CELEB, serializer))?.second?.toDomain()
        }
    }

    override fun peekCelebrityCandidates(distance: Distance?): RecordBattleCandidates? {
        if (distance != null) return null
        val store = copies ?: return null
        val member = self?.memberId() ?: return null
        val serializer = CandidatesResponse.serializer()
        return (store.peek(member, CELEB, serializer) ?: store.peek(null, CELEB, serializer))?.second?.toDomain()
    }

    override suspend fun recordTracks(kind: RecordBattleKind): Outcome<List<RecordTrack>> =
        ApiErrorMapper.call { api.recordTracks(kind.name).tracks.map { it.toDomain(kind) } }

    override suspend fun celebrityTrack(celebrityId: Long): Outcome<RecordTrack> =
        ApiErrorMapper.call { api.celebrityRecordTrack(celebrityId).toDomain(RecordBattleKind.CELEBRITY) }

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

    override suspend fun history(filter: BattleHistory.Type?, cursor: Long?): Outcome<BattleHistory> {
        val member = self?.memberId()
        return ApiErrorMapper.call {
            api.history(filter?.name, cursor, HISTORY_PAGE_SIZE).also { response ->
                if (cursor == null) keep(member, historyKey(filter), BattleHistoryResponse.serializer(), response)
            }.toDomain()
        }
    }

    override suspend fun cachedHistory(filter: BattleHistory.Type?): BattleHistory? = withContext(Dispatchers.IO) {
        val store = copies ?: return@withContext null
        val member = self?.memberId() ?: return@withContext null
        store.read(member, historyKey(filter), BattleHistoryResponse.serializer())?.second?.toDomain()
    }

    /** 받은 응답을 [member] 칸에 남긴다. [isShared]면 공용 칸에 남긴다. 회원을 모르면 내 칸에는 남기지 않는다. */
    private suspend fun <T> keep(
        member: Long?,
        key: String,
        serializer: KSerializer<T>,
        value: T,
        isShared: Boolean = false
    ) {
        val store = copies ?: return
        if (member == null && !isShared) return
        withContext(Dispatchers.IO) { store.write(member.takeUnless { isShared }, key, "", serializer, value) }
    }

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

    internal companion object {
        /** 대결 이력 한 쪽 크기(서버 PageCursor 1~100). */
        const val HISTORY_PAGE_SIZE = 20

        /** 화면 사본 열쇠(시작 미리 받기 출처 BattlePreloadSource도 쓴다). */
        const val CELEB = "celeb-candidates-all"

        fun historyKey(filter: BattleHistory.Type?): String = "battle-history-" + (filter?.name ?: "ALL")
    }
}
