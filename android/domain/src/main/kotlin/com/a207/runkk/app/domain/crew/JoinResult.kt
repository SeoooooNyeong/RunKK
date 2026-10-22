package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 가입 신청 결과(서버 crew.JoinResult). [requestId]는 승인제 신청(PENDING)에만 있다. */
data class JoinResult(val status: Status, val crewId: Long, val requestId: Long?) {
    enum class Status {
        JOINED,
        PENDING,
        CANCELLED
    }
}
