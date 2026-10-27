package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

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
