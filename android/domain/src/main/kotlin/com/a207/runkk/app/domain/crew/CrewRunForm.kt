package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루런 모집글 입력(FR-145). */
data class CrewRunForm(val title: String, val body: String?, val distance: Distance, val meetAt: Instant)
