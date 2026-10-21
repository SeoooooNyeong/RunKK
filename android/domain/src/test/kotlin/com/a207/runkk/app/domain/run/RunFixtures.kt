package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import java.time.Instant

private const val METERS_PER_DEGREE = 6_371_000.0 * Math.PI / 180.0

/** 자오선을 따라 북쪽으로 meters만큼 간 점. 하버사인 거리가 meters와 같다. */
fun north(meters: Double): GeoPoint = GeoPoint(37.0 + meters / METERS_PER_DEGREE, 127.0)

fun at(seconds: Long): Instant = T0.plusSeconds(seconds)

fun normal(seconds: Long, meters: Double): FilteredPoint = FilteredPoint(at(seconds), north(meters), SignalState.NORMAL)

fun unstable(seconds: Long, meters: Double): FilteredPoint =
    FilteredPoint(at(seconds), north(meters), SignalState.UNSTABLE)

fun sample(seconds: Long, meters: Double, accuracy: Double = 5.0): LocationSample =
    LocationSample(at(seconds), north(meters), accuracy)

fun freeSpec(plan: PlanSnapshot? = null): RunStartSpec =
    RunStartSpec(mode = RunMode.FREE, cardKind = CardKind.FREE_RUN, recordId = "rec-1", todayPlan = plan)

fun progress(
    meters: Long,
    elapsedSeconds: Long = meters / 3,
    average: Pace? = null,
    heartRate: HeartRateSample? = null
): RunProgress = RunProgress(
    elapsed = Duration.ofSeconds(elapsedSeconds),
    distance = Distance(meters),
    currentPace = null,
    averagePace = average,
    signal = SignalState.NORMAL,
    isLocked = false,
    heartRate = heartRate
)

fun context(
    progress: RunProgress,
    plan: PlanSnapshot? = null,
    gapMeters: Int? = null,
    rank: CrewRank? = null,
    birthYear: Int? = null,
    settings: GuidanceSettings = GuidanceSettings.DEFAULT
): GuidanceContext = GuidanceContext(progress, plan, gapMeters, rank, birthYear, settings)
