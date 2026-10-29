package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordDetail

/**
 * 대결 결과 2쪽(상세, D-283)이 쓰는 본인 기록 상세(GET /runs/{id}). 기록 저장소의 연산 하나만 쓰므로 그 하나로 좁혀 받는다
 * (BattleEntryModule이 RecordRepository.run을 잇는다).
 */
fun interface RunDetailSource {
    suspend fun detail(recordId: String): Outcome<RecordDetail>
}
