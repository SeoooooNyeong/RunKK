package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 성장 분석(서버 analysis.GrowthView, FR-061~FR-063). */
data class Growth(val periods: List<PeriodTotal>, val bests: Map<String, Duration>, val longest: Distance?) {
    data class PeriodTotal(val start: LocalDate, val distance: Distance, val runs: Int, val averagePace: Pace?)
}
