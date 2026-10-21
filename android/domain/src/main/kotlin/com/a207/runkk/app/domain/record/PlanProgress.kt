package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 계획 진행(서버 analysis.PlanProgressView, FR-073·FR-187). */
data class PlanProgress(val days: List<Day>) {
    data class Day(val date: LocalDate, val planned: Distance?, val done: Distance, val supplement: Distance?)
}
