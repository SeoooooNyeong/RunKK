package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 기록 목록·상세 공용 요약 한 줄. valid가 거짓이면 "집계 제외"로 보인다. */
data class RunSummary(
    val recordId: String,
    val startedAt: Instant,
    val distance: Distance,
    val elapsed: Duration,
    val averagePace: Pace?,
    val valid: Boolean,
    val mode: RunMode,
    val cardKind: CardKind
)
