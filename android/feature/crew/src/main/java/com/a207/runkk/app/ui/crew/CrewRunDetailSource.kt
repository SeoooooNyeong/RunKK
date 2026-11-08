package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.ui.share.RecordResultDetail

/**
 * 크루런 결과 상세(F-16b)가 쓰는 내 기록 상세 한 장(GET /runs/{id}). ui.crew는 domain.record를 보지 않으므로(5.1.2) ui.share의
 * RecordResultDetailReader로 잇는다(CrewRunDetailModule).
 */
fun interface CrewRunDetailSource {
    suspend fun detail(recordId: String): Outcome<RecordResultDetail>
}
