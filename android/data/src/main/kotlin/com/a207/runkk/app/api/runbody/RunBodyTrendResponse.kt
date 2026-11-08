package com.a207.runkk.app.api.runbody

import kotlinx.serialization.Serializable

/** GET /runbody/trend(계약 v1.2 8절). [recent]는 round 0이면, [previous]는 round 2 미만이면 null이다. */
@Serializable
internal data class RunBodyTrendResponse(
    val validRunCount: Int = 0,
    val round: Int = 0,
    val needs: Int = 0,
    val recent: Window? = null,
    val previous: Window? = null,
    val sameHeartRatePaceChange: Int? = null,
    val runimalType: String? = null
) {
    @Serializable
    data class Window(
        val fromOrdinal: Int,
        val toOrdinal: Int,
        val paceSecondsPerKm: Int,
        val runsPerWeek: Double = 0.0,
        val runsPerMonth: Double = 0.0,
        val averageHeartRate: Int? = null
    )
}
