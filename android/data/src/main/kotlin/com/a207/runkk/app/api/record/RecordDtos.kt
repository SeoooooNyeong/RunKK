package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

// 기록 분석 REST 전송 형식(서버 analysis …View). 거리는 미터, 시간은 초, 페이스는 km당 초, 시각은 ServerTime, 날짜는 yyyy-MM-dd.
// 필드 이름은 10/21 API 계약 전 앱 쪽 제안이고 api.record 밖으로 나가지 않는다.

@Serializable
internal data class GeoPointDto(val latitude: Double, val longitude: Double)

@Serializable
internal data class RunSummaryDto(
    val recordId: String,
    val startedAt: String,
    val distanceMeters: Long,
    val elapsedSeconds: Long,
    val averagePaceSecondsPerKm: Int? = null,
    val valid: Boolean,
    val mode: String,
    val cardKind: String
)

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

@Serializable
internal data class RecordListResponse(val runs: List<RunSummaryDto> = emptyList())

/**
 * unstableSegments는 시작 기준 초 구간과 거리다. 서버가 함께 주는 unstableRanges(초를 경로 포인트 번호로 근사)는 읽지 않는다.
 * heartRateZones 값은 초.
 */
@Serializable
internal data class RecordDetailResponse(
    val summary: RunSummaryDto,
    val route: List<GeoPointDto> = emptyList(),
    val unstableSegments: List<UnstableDto> = emptyList(),
    val calories: Int? = null,
    val heartRateZones: Map<String, Long> = emptyMap(),
    val newRecords: List<String> = emptyList(),
    val crewContributionMeters: Long? = null,
    val rewardPoints: Int? = null,
    val weather: String? = null,
    val splitPaces: List<Int> = emptyList(),
    val fastestKmSecondsPerKm: Int? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null
) {
    /** 신호 불안정 구간. 서버가 빠뜨린 항목은 0으로 읽어 기록 상세 전체가 실패하지 않게 한다. */
    @Serializable
    data class UnstableDto(val fromSeconds: Long = 0, val toSeconds: Long = 0, val distanceMeters: Long = 0)
}

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

@Serializable
internal data class ReportResponse(
    val summary: String? = null,
    val comment: String? = null,
    val commentSource: String? = null,
    val message: String? = null
)

@Serializable
internal data class RankingResponse(
    val entries: List<Entry> = emptyList(),
    val mine: Entry? = null,
    val gapToNextMeters: Long? = null,
    val aggregatedAt: String? = null,
    val notice: String? = null
) {
    @Serializable
    data class Entry(val rank: Int, val name: String, val distanceMeters: Long)
}

@Serializable
internal data class WeeklyGoalRequest(val runs: Int, val distanceMeters: Long)

@Serializable
internal data class PlanRequest(val distanceMeters: Long, val paceSecondsPerKm: Int?)

@Serializable
internal data class PlanProgressResponse(val days: List<Day> = emptyList()) {
    @Serializable
    data class Day(
        val date: String,
        val plannedMeters: Long? = null,
        val doneMeters: Long = 0,
        val supplementMeters: Long? = null
    )
}

@Serializable
internal data class TodayPlanResponse(val distanceMeters: Long? = null, val paceSecondsPerKm: Int? = null)

/** 공유 카드 값(서버 running.RunCardData). route는 코스지도 공유가 켜졌거나 본인일 때만 온다. */
@Serializable
internal data class RunCardResponse(
    val recordId: String,
    val cardKind: String,
    val startedAt: String,
    val distanceMeters: Long,
    val elapsedSeconds: Long,
    val averagePaceSecondsPerKm: Int? = null,
    val route: List<GeoPointDto> = emptyList(),
    val courseMapShared: Boolean = false
)
