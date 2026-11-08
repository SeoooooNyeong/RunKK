package com.a207.runkk.app.api.runbody

import kotlinx.serialization.Serializable

/**
 * GET /runbody/months/{yearMonth}: ai MonthlyOut 본문 그대로. 앱이 쓰지 않는 필드(avatarInputs 등)는 받지 않는다. [asOf]는 부하 준비 중 지난 날
 * 수의 기준 시점이다(MR !91 e2e 6).
 */
@Serializable
internal data class RunBodyMonthResponse(
    val yearMonth: String,
    val sessionCount: Int = 0,
    val axes: Map<String, Axis> = emptyMap(),
    val bests: Bests = Bests(),
    val weeks: List<Week> = emptyList(),
    val timeOfDay: TimeOfDay = TimeOfDay(),
    val load: Load,
    val baseline: Baseline,
    val asOf: String? = null
) {
    @Serializable
    data class Axis(val average: Int? = null, val count: Int = 0)

    @Serializable
    data class Best(val value: Long)

    @Serializable
    data class Bests(
        val fastestKmSeconds: Best? = null,
        val fastest2kmSeconds: Best? = null,
        val longestRunMeters: Best? = null,
        val bestRecoveryHeartRate1m: Best? = null
    )

    @Serializable
    data class Week(val weekStart: String, val distanceMeters: Long)

    @Serializable
    data class TimeOfDay(val night: Int = 0, val day: Int = 0)

    @Serializable
    data class Load(
        val available: Boolean,
        val ratio: Double? = null,
        val level: String? = null,
        val acuteMeters: Long = 0,
        val chronicWeeklyMeters: Long = 0,
        val availableFrom: String? = null
    )

    @Serializable
    data class Baseline(
        val available: Boolean,
        val sessionCount: Int = 0,
        val needs: Int = 0,
        val paceSecondsPerKm: Int? = null,
        val avgHeartRate: Int? = null
    )
}
