package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.SignalState
import java.time.Instant

/** 위치 측정값(측정일시, 좌표, 정확도 반경 m). data.device.MeasurementService가 넘긴다(FR-032). */
data class LocationSample(val at: Instant, val point: GeoPoint, val accuracyMeters: Double)

/** LocationFilter를 통과한 포인트와 신호 상태(FR-033). */
data class FilteredPoint(val at: Instant, val point: GeoPoint, val state: SignalState)

/** 기록에 담는 경로 포인트(CompletedRun.route). */
data class RoutePoint(val at: Instant, val point: GeoPoint, val state: SignalState)

/** 불안정 구간: 시작·끝 일시와 건너뛴 직선 거리(누적 거리에서 뺀다, FR-033, Q-18). */
data class UnstableSegment(val startAt: Instant, val endAt: Instant, val distance: Distance)

/** 검증된 심박 표본(측정일시, bpm). 실패 측정은 data.watch가 걸러서 넘긴다(FR-043, D-54). */
data class HeartRateSample(val at: Instant, val bpm: Int)
