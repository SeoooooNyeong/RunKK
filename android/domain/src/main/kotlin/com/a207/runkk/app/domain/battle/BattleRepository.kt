package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome

/** 대결 REST 포트(5.7.1 /battles/…). 구현 api.battle. 확인 단계("정말 할까요")는 화면이 하고 확인된 요청만 보낸다. */
interface BattleRepository {
    /** 지금 매칭 대기 여부와 기본 대결 거리(GET /battles/match, FR-109). 배틀런 거리 고르기의 기본값이다. */
    suspend fun matchStatus(): Outcome<MatchAcceptance>

    suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance>

    suspend fun cancel(): Outcome<MatchAcceptance>

    suspend fun selfCandidates(): Outcome<RecordBattleCandidates>

    suspend fun celebrityCandidates(distance: Distance?): Outcome<RecordBattleCandidates>

    suspend fun startSelfBattle(distance: Distance): Outcome<RecordBattleStart>

    suspend fun startCelebrityBattle(celebrityId: Long): Outcome<RecordBattleStart>

    /** 기록 대결 시작 보고(POST /battles/{id}/started, R-02). */
    suspend fun markStarted(battleId: Long): Outcome<Unit>

    /** 그만두기(POST /battles/{id}/forfeit, design-review.md E3). 출발한 대결은 패배로 판정한다(셀프런·셀럽런 포함, 2026-10-30). */
    suspend fun forfeit(battleId: Long): Outcome<Unit>

    /** 셀프런·셀럽런 취소(POST /battles/{id}/cancel): 승패·기록 없이 대결을 취소한다(사용자 요청 2026-10-30). */
    suspend fun cancel(battleId: Long): Outcome<Unit>

    /** 진행 중인 대결(GET /battles/active). 없으면(204) Success(null)이다. */
    suspend fun active(): Outcome<ActiveBattle?>

    suspend fun result(battleId: Long): Outcome<BattleResult>

    /**
     * filter가 RANDOM이면 서버가 GHOST도 함께 준다(BattleType.historyGroup). [cursor]가 null이면 첫 쪽이고,
     * 결과의 nextCursor가 null이면 끝이다.
     */
    suspend fun history(filter: BattleHistory.Type?, cursor: Long? = null): Outcome<BattleHistory>

    /** 친구 대결 초대 만들기(POST /battles/friend-invites, D-270). 거부는 Rejected(INVITE_ALREADY_PENDING 등). */
    suspend fun createFriendInvite(distance: Distance): Outcome<FriendInvite>

    /** 내가 보낸 열린 초대(GET /battles/friend-invites/mine, D-271). 없으면(204) Success(null)이다. */
    suspend fun myFriendInvite(): Outcome<FriendInvite?>

    /** 초대 취소(DELETE /battles/friend-invites/{inviteId}). 끝난 초대도 성공이다. 이미 참가했으면 Rejected(INVITE_ALREADY_JOINED). */
    suspend fun cancelFriendInvite(inviteId: Long): Outcome<Unit>

    /** 받은 초대의 참가 확인 정보(GET …/by-token/{token}, D-273). 없는 토큰은 Rejected(INVITE_NOT_FOUND). */
    suspend fun friendInvite(token: String): Outcome<FriendInviteView>

    /** 참가(POST …/by-token/{token}/accept, D-274). 성공하면 대결 ID이고, 서버가 이어서 MatchFound·BattleStart를 보낸다. */
    suspend fun acceptFriendInvite(token: String): Outcome<Long>

    /** 거절(POST …/by-token/{token}/decline, D-273). */
    suspend fun declineFriendInvite(token: String): Outcome<Unit>
}
