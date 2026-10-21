package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루런 상태(서버 crew.CrewRunStatus). */
enum class CrewRunStatus {
    VOTING,
    LOBBY_OPEN,
    RUNNING,
    FINISHED,
    CANCELLED
}
