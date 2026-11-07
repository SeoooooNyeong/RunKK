package com.a207.runkk.app.api.runbody

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.runbody.RunBodyAxis
import com.a207.runkk.app.domain.runbody.RunBodyMonth
import com.a207.runkk.app.domain.runbody.RunBodyResult
import com.a207.runkk.app.domain.runbody.RunBodySession
import com.a207.runkk.app.domain.runbody.RunBodyStatus
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** 모르는 상태는 화면을 고르지 않도록 해독 실패로 본다(ApiErrorMapper가 Server(0)으로 바꾼다). */
internal fun RunBodySessionResponse.toDomain(): RunBodySession {
    val known = requireNotNull(enumByName<RunBodyStatus>(status)) { "모르는 런바디 상태 $status" }
    return RunBodySession(recordId, known, result?.takeIf { known == RunBodyStatus.DONE }?.toDomain())
}

private fun RunBodySessionResponse.Result.toDomain(): RunBodyResult = RunBodyResult(
    summary = RunBodyResult.Summary(
        distance = meters(summary.distanceMeters),
        active = Duration.ofMillis(summary.activeMs.coerceAtLeast(0)),
        pace = pace(summary.paceSecondsPerKm),
        averageHeartRate = summary.avgHeartRate,
        maxHeartRate = summary.maxHeartRate,
        startedAt = ServerTime.parse(summary.startedAt)
    ),
    axes = RunBodyAxis.entries.associateWith { axis ->
        val dto = axes[axis.wireName()]
        RunBodyResult.AxisScore(
            score = dto?.score,
            label = dto?.label.orEmpty(),
            level = enumByName<RunBodyResult.AxisScore.Level>(dto?.level?.uppercase(Locale.ROOT))
                ?: RunBodyResult.AxisScore.Level.NONE
        )
    },
    laps = laps.map { RunBodyResult.Lap(it.km, pace(it.paceSeconds), it.avgHeartRate, it.partial) },
    previous = prev?.let { RunBodyResult.Previous(meters(it.distanceMeters), pace(it.paceSecondsPerKm)) },
    feedback = RunBodyResult.Feedback(feedback.headline, feedback.good, feedback.fix, feedback.next),
    coaching = coaching.flags.mapNotNull { enumByName<RunBodyResult.Coaching>(it.uppercase(Locale.ROOT)) },
    split = coaching.detail.split?.let { s ->
        val first = pace(s.firstHalfPaceS)
        val second = pace(s.secondHalfPaceS)
        if (first != null && second != null) RunBodyResult.Split(first, second) else null
    }
)

internal fun RunBodyMonthResponse.toDomain(): RunBodyMonth = RunBodyMonth(
    month = YearMonth.parse(yearMonth),
    sessionCount = sessionCount,
    averages = RunBodyAxis.entries.mapNotNull { axis -> axes[axis.wireName()]?.average?.let { axis to it } }.toMap(),
    bests = RunBodyMonth.Bests(
        fastestKm = bests.fastestKmSeconds?.let { Duration.ofSeconds(it.value) },
        fastest2Km = bests.fastest2kmSeconds?.let { Duration.ofSeconds(it.value) },
        longest = bests.longestRunMeters?.let { meters(it.value) },
        recoveryHeartRate1m = bests.bestRecoveryHeartRate1m?.value?.toInt()
    ),
    weeks = weeks.map { RunBodyMonth.Week(LocalDate.parse(it.weekStart), meters(it.distanceMeters)) },
    nightRuns = timeOfDay.night,
    dayRuns = timeOfDay.day,
    baseline = RunBodyMonth.Baseline(
        isAvailable = baseline.available,
        sessionCount = baseline.sessionCount,
        needs = baseline.needs,
        pace = pace(baseline.paceSecondsPerKm),
        averageHeartRate = baseline.avgHeartRate
    ),
    load = RunBodyMonth.Load(
        isAvailable = load.available,
        ratio = load.ratio,
        level = enumByName<RunBodyMonth.Load.Level>(load.level?.uppercase(Locale.ROOT)),
        acute = meters(load.acuteMeters),
        chronicWeekly = meters(load.chronicWeeklyMeters),
        availableFrom = load.availableFrom?.let(LocalDate::parse)
    )
)

/** ai 축 이름(intensity 등). */
private fun RunBodyAxis.wireName(): String = name.lowercase(Locale.ROOT)

private fun meters(value: Long): Distance = Distance(value.coerceAtLeast(0))

private fun pace(seconds: Int?): Pace? = seconds?.takeIf { it > 0 }?.let(::Pace)
