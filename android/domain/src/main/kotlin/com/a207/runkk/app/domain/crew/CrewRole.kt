package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루 안 역할(서버 crew.CrewRole). */
enum class CrewRole {
    LEADER,
    MEMBER
}
