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

    /**
     * 기기에 남긴 셀럽 후보 사본(S15P31A207-345 2차, 시작 로딩이 받아 둔 전체 거리 목록). 내 계정 칸의 것(내 최고 기록 포함)이 없으면
     * 공용 칸의 것(내 기록 없음)을 준다. [distance]가 null(전체)일 때만 있고 없으면 null. 네트워크를 쓰지 않는다.
     */
    suspend fun cachedCelebrityCandidates(distance: Distance?): RecordBattleCandidates? = null

    /**
     * [cachedCelebrityCandidates]를 기다리지 않고(파일을 읽지 않고) 이미 읽어 둔 사본만 준다(S15P31A207-345 9회차). 시작 로딩이 받아 둔
     * 사본이면 있다. 셀럽 탭이 첫 상태를 이것으로 시작해 로딩 원을 한 프레임도 그리지 않는다. 없으면 null.
     */
    fun peekCelebrityCandidates(distance: Distance?): RecordBattleCandidates? = null

    /**
     * 셀프·셀럽 상대 기록과 진행용 구간 기록을 미리 받는다(GET /battles/record-tracks?kind=, D-377, U3 (나)). 서버에 대결을 만들지 않는다.
     * 셀프런은 거리별 내 최고 기록(기록 없는 거리는 없음), 셀럽런은 공개된 셀럽 기록 전부다. 거리 오름차순, 같은 거리는 완주 시간 오름차순.
     * 셀럽런 목록은 구간 기록이 비어 있다(W31): 고를 때 [celebrityTrack]으로 받는다.
     */
    suspend fun recordTracks(kind: RecordBattleKind): Outcome<List<RecordTrack>>

    /**
     * 셀럽 기록 하나와 진행용 구간 기록(GET /battles/record-tracks/celebrity/{celebrityId}, W31). 서버에 대결을 만들지 않는다.
     * 없거나 공개되지 않았거나 구간 기록을 만들 수 없으면 Rejected(404 본문 code, 없으면 NOT_FOUND)다.
     */
    suspend fun celebrityTrack(celebrityId: Long): Outcome<RecordTrack>

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

    /** 기기에 남긴 대결 이력 첫 쪽 사본(내 계정 칸, [filter]마다). 없으면 null. 네트워크를 쓰지 않는다(S15P31A207-345 2차). */
    suspend fun cachedHistory(filter: BattleHistory.Type?): BattleHistory? = null

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
