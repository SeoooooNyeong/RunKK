package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 이번 주 목표 달성(서버 analysis.WeeklyAchievement, FR-056). 백분율은 100을 넘을 수 있다. */
data class WeeklyGoalProgress(
    val goalRuns: Int,
    val goalDistance: Distance,
    val actualRuns: Int,
    val actualDistance: Distance,
    val runsPercent: Int,
    val distancePercent: Int
)
