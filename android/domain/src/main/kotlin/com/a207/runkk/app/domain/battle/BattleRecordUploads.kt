package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.domain.run.PendingRunRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * 대결 기록 업로드 알림(배틀 QA 5번). ui.battle은 domain.run을 보지 않으므로(5.1.2) 미전송 기록 목록을 대결 기준으로 바꿔 준다.
 * 그만두기·오프라인 종료 뒤 결과가 비교표 없이 왔거나 읽지 못했을 때, 기록이 올라가면 결과 화면이 다시 읽는다.
 */
class BattleRecordUploads
@Inject
constructor(private val pendingRuns: PendingRunRepository) {
    /**
     * 구독 뒤 미전송 목록이 바뀔 때마다 [battleId] 기록이 목록에 없으면 낸다(올라갔거나 처음부터 없음). 구독할 때의 목록은 받는 쪽이
     * 이미 읽은 상태라 건너뛴다. 목록 방출이 합쳐져 저장과 전송을 한 번에 보더라도 놓치지 않게 전이가 아니라 "지금 없음"을 낸다.
     * 다시 읽을지는 받는 쪽이 정한다.
     */
    fun settled(battleId: Long): Flow<Unit> =
        pendingRuns.observePending().drop(1).filter { runs -> runs.none { it.battleId == battleId } }.map { }
}
