package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class DashboardResponse(
    val todayMeters: Long,
    val totalMeters: Long,
    val landmark: String? = null,
    val streakDays: Int = 0,
    /** 연속 러닝 주 수(유효 러닝이 1번 이상인 주, 사용자 요청 2026-10-31). 옛 서버는 주지 않는다. */
    val streakWeeks: Int = 0,
    val grass: Map<String, Long> = emptyMap(),
    val weeklyAchievement: Double? = null,
    val crew: CrewArea? = null,
    val todaySeconds: Long = 0,
    val todayRuns: Int = 0,
    val weekly: WeeklyDto? = null,
    val grassLevels: Map<String, Int> = emptyMap(),
    /** 리워드 잔액(v0.14, 서버 D-236). 옛 서버는 주지 않는다(D-289). 가정: 계약 자료 갱신 전 서버 대조 필요. */
    val rewardBalance: Int? = null,
    /** baseDate 달의 유효 러닝 횟수·거리(v0.14 기록 화면 월 요약, 서버 D-236). 옛 서버는 주지 않는다(D-289). */
    val monthRuns: Int? = null,
    val monthMeters: Long? = null,
    /** 최근 러닝 1건(기록 목록 맨 위 행과 같은 형식, 05-03 5.7.3.3 P8). 기록이 없거나 옛 서버면 null. */
    val latestRun: RunSummaryDto? = null
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
