package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 기록 상세(서버 analysis.RecordDetailView, srs.md 6.3 3.2). route는 본인이거나 코스지도공유 켜짐일 때만 온다.
 * [unstableSegments]는 신호 불안정 구간(시작 기준 경과 시간, FR-033·FR-059)이다. 서버의 unstableRanges(초를 경로 포인트 번호로
 * 근사한 값)는 걸러진 위치가 빠지면 번호가 어긋나므로 읽지 않는다. [splitPaces]는 1km 구간 페이스, [fastestKm]은 가장 빠른 1km.
 * v0.14(D-280, D-284): [isDeletable]은 "기록 삭제" 버튼(서버 deletable, 옛 서버면 본인이고 집계 제외일 때), [evaluation]은 이 러닝이
 * 일으킨 티어 평가(없으면 null), [temperatureC]·[sky]는 날씨(서버 SkyCondition 이름), [pauseCount]·[paused]는 정지 구간 수와 합계다.
 * [recordVisibility]는 본인 기록일 때만 오는 공개 범위(서버 RecordVisibility 이름: PUBLIC·CREW·PRIVATE)이고 남의 기록이나 옛 서버면 null이다.
 */
data class RecordDetail(
    val summary: RunSummary,
    val route: List<GeoPoint>,
    val unstableSegments: List<Unstable>,
    val calories: Int?,
    val heartRateZones: Map<String, Duration>,
    val newRecords: List<String>,
    val crewContribution: Distance?,
    val rewardPoints: Int?,
    val weather: String?,
    val splitPaces: List<Pace> = emptyList(),
    val fastestKm: Pace? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val isDeletable: Boolean = false,
    val evaluation: Evaluation? = null,
    val temperatureC: Int? = null,
    val sky: String? = null,
    val pauseCount: Int = 0,
    val paused: Duration = Duration.ZERO,
    val recordVisibility: String? = null
) {
    /** 불안정 구간 하나: 시작·끝 경과 시간과 구간 거리. */
    data class Unstable(val from: Duration, val to: Duration, val distance: Distance)
}
