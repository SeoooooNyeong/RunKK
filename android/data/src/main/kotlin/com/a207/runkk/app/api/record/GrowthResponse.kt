package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

/** bests 값은 기준 거리별 최고 기록(초). */
@Serializable
internal data class GrowthResponse(
    val periods: List<PeriodTotal> = emptyList(),
    val bests: Map<String, Long> = emptyMap(),
    val longestMeters: Long? = null
) {
    @Serializable
    data class PeriodTotal(
        val start: String,
        val distanceMeters: Long,
        val runs: Int,
        val averagePaceSecondsPerKm: Int? = null
    )
}
