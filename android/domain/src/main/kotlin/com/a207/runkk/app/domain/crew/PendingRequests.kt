package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 대기 중 가입 신청(서버 crew.PendingRequestsView). 티어·레벨·나이대·성별은 F-25 신청 카드 보조 줄(공개한 값만, contract-W7 3장). */
data class PendingRequests(val requests: List<Request>) {
    data class Request(
        val requestId: Long,
        val memberId: Long,
        val displayName: String,
        val requestedAt: Instant,
        val tier: String? = null,
        val level: Int? = null,
        val ageBand: String? = null,
        val gender: Gender? = null
    )
}
