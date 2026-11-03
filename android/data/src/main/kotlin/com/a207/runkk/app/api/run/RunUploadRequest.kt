package com.a207.runkk.app.api.run

import kotlinx.serialization.Serializable

/**
 * POST /runs 요청(서버 RunUploadCommand, A-04). recordId는 앱 UUID(D-11)이고 pointCount는 포인트 수 확인용이다(NFR-010).
 * 경로·심박은 points·heartRates에만 있다. startDevice는 서버 running.StartDevice 이름(PHONE·WATCH, W23)이다.
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
    val startDevice: String
) {
    /** 정지 구간(서버 RunController.PauseRequest, FR-194). 자유런만 비어 있지 않다. */
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
