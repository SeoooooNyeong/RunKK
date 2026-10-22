package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루런 참가 투표 답(서버 crew.VoteAnswer). */
enum class VoteAnswer {
    GOING,
    NOT_GOING
}
