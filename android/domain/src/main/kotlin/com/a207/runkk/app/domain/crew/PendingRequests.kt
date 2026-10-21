package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 대기 중 가입 신청(서버 crew.PendingRequestsView). */
data class PendingRequests(val requests: List<Request>) {
    data class Request(val requestId: Long, val memberId: Long, val displayName: String, val requestedAt: Instant)
}
