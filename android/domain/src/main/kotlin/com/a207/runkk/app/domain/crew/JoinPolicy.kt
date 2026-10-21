package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 가입 방식(서버 crew.JoinPolicy). */
enum class JoinPolicy {
    APPROVAL,
    FREE
}
