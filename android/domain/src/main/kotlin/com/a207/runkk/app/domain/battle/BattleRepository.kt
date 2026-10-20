package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome

/** 대결 REST 포트(5.7.1 /battles/…). 구현 api.battle. 확인 단계("정말 할까요")는 화면이 하고 확인된 요청만 보낸다. */
interface BattleRepository {
    suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance>

    suspend fun cancel(): Outcome<MatchAcceptance>

    suspend fun selfCandidates(): Outcome<RecordBattleCandidates>

    suspend fun celebrityCandidates(distance: Distance?): Outcome<RecordBattleCandidates>

    suspend fun startSelfBattle(distance: Distance): Outcome<RecordBattleStart>

    suspend fun startCelebrityBattle(celebrityId: Long): Outcome<RecordBattleStart>

    /** 기록 대결 시작 보고(POST /battles/{id}/started, R-02). */
    suspend fun markStarted(battleId: Long): Outcome<Unit>

    /** 그만두기(POST /battles/{id}/forfeit, design-review.md E3). */
    suspend fun forfeit(battleId: Long): Outcome<Unit>

    suspend fun result(battleId: Long): Outcome<BattleResult>

    /** filter가 RANDOM이면 서버가 GHOST도 함께 준다(BattleType.historyGroup). */
    suspend fun history(filter: BattleHistory.Type?): Outcome<BattleHistory>
}
