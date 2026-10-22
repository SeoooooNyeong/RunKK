package com.a207.runkk.app.api.run

import kotlinx.serialization.Serializable

// 기록 전송 형식(api.run). 시각은 api.core.ServerTime.formatWithOffset 형식(Asia/Seoul 오프셋 포함, 서버가 OffsetDateTime으로 받는다), 거리는 미터다.

/**
 * POST /runs 요청(서버 RunUploadCommand, A-04). recordId는 앱 UUID(D-11)이고 pointCount는 포인트 수 확인용이다(NFR-010).
 * 경로·심박은 points·heartRates에만 있다.
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
    val heartRates: List<HeartRate>
) {
    /** signal은 서버 running.SignalState 이름(NORMAL / UNSTABLE). */
    @Serializable
    data class Point(val measuredAt: String, val latitude: Double, val longitude: Double, val signal: String)

    @Serializable
    data class Segment(val from: String, val to: String, val distanceMeters: Long)

    @Serializable
    data class HeartRate(val measuredAt: String, val bpm: Int)
}

/** POST /runs 응답(서버 RunController.UploadResponse). status는 STORED 또는 ALREADY_STORED, valid는 유효 러닝 여부다. */
@Serializable
internal data class UploadResponse(val recordId: String, val status: String, val valid: Boolean = true)

/** POST /runs/{id}/heart-rates 요청(서버 LateHeartRateCommand). */
@Serializable
internal data class LateHeartRatesRequest(val samples: List<RunUploadRequest.HeartRate>)

/** GET /me/disposals 응답 한 줄(서버 DisposalOrderView: id·kind·dueAt·status). kind는 ROUTE / HEART_RATE. */
@Serializable
internal data class DisposalOrderResponse(val id: Long, val kind: String)
