package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루 공개 범위(서버 crew.CrewVisibility). */
enum class CrewVisibility {
    PUBLIC,
    PRIVATE
}
