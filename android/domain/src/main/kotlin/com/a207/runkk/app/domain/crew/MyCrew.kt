package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 본인 크루 현황(서버 crew.MyCrewView): 소속 크루(없으면 null)와 대기 중인 본인 가입 신청. */
data class MyCrew(
    val crew: CrewDetail?,
    val pendingRequests: List<PendingJoin>,
    /** 24시간 가입·크루 만들기 제한 중이면 풀리는 시각, 아니면 null(서버 rejoinAvailableAt, contract-W4 3절). */
    val joinableAt: Instant? = null
) {
    /** 대기 중인 본인 가입 신청 한 건(취소는 CrewRepository.cancelJoinRequest). */
    data class PendingJoin(val requestId: Long, val crewId: Long, val crewName: String, val requestedAt: Instant)
}
