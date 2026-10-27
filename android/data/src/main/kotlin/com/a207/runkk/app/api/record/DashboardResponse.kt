package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class DashboardResponse(
    val todayMeters: Long,
    val totalMeters: Long,
    val landmark: String? = null,
    val streakDays: Int = 0,
    val grass: Map<String, Long> = emptyMap(),
    val weeklyAchievement: Double? = null,
    val crew: CrewArea? = null,
    val todaySeconds: Long = 0,
    val todayRuns: Int = 0,
    val weekly: WeeklyDto? = null,
    val grassLevels: Map<String, Int> = emptyMap()
) {
    /** 이번 주 목표·실적. 서버가 빠뜨린 항목은 0으로 읽어 대시보드 전체가 실패하지 않게 한다. */
    @Serializable
    data class WeeklyDto(
        val goalRuns: Int = 0,
        val goalMeters: Long = 0,
        val actualRuns: Int = 0,
        val actualMeters: Long = 0,
        val runsPercent: Int = 0,
        val distancePercent: Int = 0
    )

    @Serializable
    data class CrewArea(
        val crewName: String,
        val streakDays: Int = 0,
        val grass: Map<String, Long> = emptyMap(),
        val landmark: String? = null
    )
}
