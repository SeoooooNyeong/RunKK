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
    val contributedCrewName: String? = null,
    val rewardPoints: Int? = null,
    val weather: String? = null,
    val splitPaces: List<Int> = emptyList(),
    val fastestKmSecondsPerKm: Int? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    // v0.14(서버 D-235·D-291): 옛 서버가 주지 않으면 기본값(D-289). deletable이 없으면 owner·valid로 판정한다.
    val owner: Boolean? = null,
    val temperatureC: Int? = null,
    val sky: String? = null,
    val pauses: List<PauseDto> = emptyList(),
    val pausedSeconds: Long = 0,
    val deletable: Boolean? = null,
    val evaluation: EvaluationDto? = null,
    // 본인 기록일 때만 RecordVisibility 이름(서버 BE2). 남의 기록·옛 서버는 null.
    val recordVisibility: String? = null
) {
    /** 정지 구간 하나(서버 PauseDto). 화면은 개수만 쓴다. */
    @Serializable
    data class PauseDto(val from: String? = null, val to: String? = null)

    /** 이 러닝이 일으킨 티어 평가(서버 EvaluationDto, D-291·D-294). tier가 없으면 평가 없음으로 본다. */
    @Serializable
    data class EvaluationDto(
        val kind: String? = null,
        val tier: String? = null,
        val validRuns: Int = 0,
        val runimalType: String? = null
    )

    /** 신호 불안정 구간. 서버가 빠뜨린 항목은 0으로 읽어 기록 상세 전체가 실패하지 않게 한다. */
    @Serializable
    data class UnstableDto(val fromSeconds: Long = 0, val toSeconds: Long = 0, val distanceMeters: Long = 0)
}
