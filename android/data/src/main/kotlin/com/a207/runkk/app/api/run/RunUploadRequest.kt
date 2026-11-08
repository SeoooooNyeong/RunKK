package com.a207.runkk.app.api.run

import kotlinx.serialization.Serializable

/**
 * POST /runs 요청(서버 RunUploadCommand, A-04). recordId는 앱 UUID(D-11)이고 pointCount는 포인트 수 확인용이다(NFR-010).
 * 경로·심박은 points·heartRates에만 있다. startDevice는 서버 running.StartDevice 이름(PHONE·WATCH, W23)이다.
 * motions는 기기가 잰 속도·케이던스다(선택, S15P31A207-242). 옛 서버는 모르는 항목을 버린다.
 */
@Serializable
internal data class RunUploadRequest(
    val recordId: String,
    val mode: String,
    val cardKind: String,
    val battleId: Long?,
    val crewRunPostId: Long?,
    val startedAt: String,
    val endedAt: String,
    val elapsedMillis: Long,
    val distanceMeters: Long,
    val pointCount: Int,
    val points: List<Point>,
    val unstableSegments: List<Segment>,
    val heartRates: List<HeartRate>,
    val pauses: List<Pause>,
    val startDevice: String,
    val motions: List<Motion> = emptyList()
) {
    /** 기기가 잰 움직임 표본(서버 RunController.MotionRequest). 재지 못한 값은 null이다. */
    @Serializable
    data class Motion(val measuredAt: String, val speedMps: Double?, val cadenceSpm: Double?)

    /** 정지 구간(서버 RunController.PauseRequest, FR-194). 자유런·크루런만 비어 있지 않다(FR-300). */
    @Serializable
    data class Pause(val from: String, val to: String)

    /** signal은 서버 running.SignalState 이름(NORMAL / UNSTABLE). */
    @Serializable
    data class Point(val measuredAt: String, val latitude: Double, val longitude: Double, val signal: String)

    @Serializable
    data class Segment(val from: String, val to: String, val distanceMeters: Long)

    @Serializable
    data class HeartRate(val measuredAt: String, val bpm: Int)
}
