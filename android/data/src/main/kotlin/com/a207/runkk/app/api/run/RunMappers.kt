package com.a207.runkk.app.api.run

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.thinnedTo
import com.a207.runkk.app.domain.sync.DisposalOrder
import com.a207.runkk.app.domain.sync.UploadReceipt
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// CompletedRun → RunUploadRequest 매퍼(6.1.14 api.run, D-54)와 응답 → 도메인 매퍼.
// elapsedMillis는 운동 시간(정지 제외)이고 endedAt은 시작 + 운동 시간 + 정지 시간 합(CompletedRun.endedAt)이다(서버 규칙: 정지 구간은 시작~종료 안, F.2.2).

internal fun CompletedRun.toRequest(): RunUploadRequest = RunUploadRequest(
    recordId = recordId,
    mode = mode.name,
    cardKind = cardKind.name,
    battleId = battleId,
    crewRunPostId = crewRunPostId,
    startedAt = ServerTime.formatWithOffset(startedAt),
    endedAt = ServerTime.formatWithOffset(endedAt),
    elapsedMillis = elapsed.toMillis(),
    distanceMeters = distance.meters,
    pointCount = route.size,
    points =
        route.map { p ->
            RunUploadRequest.Point(ServerTime.formatWithOffset(p.at), p.point.latitude, p.point.longitude, p.state.name)
        },
    unstableSegments =
        unstable.map { s ->
            RunUploadRequest.Segment(
                ServerTime.formatWithOffset(s.startAt),
                ServerTime.formatWithOffset(s.endAt),
                s.distance.meters
            )
        },
    heartRates = heartRates.map { it.toRequest() },
    pauses = pauses.map {
        RunUploadRequest.Pause(ServerTime.formatWithOffset(it.from), ServerTime.formatWithOffset(it.to))
    },
    startDevice = startDevice.name,
    motions = motions.map { RunUploadRequest.Motion(ServerTime.formatWithOffset(it.at), it.speedMps, it.cadenceSpm) }
)

/**
 * 업로드 본문 상한(바이트). nginx가 POST /api/v1/runs에 32MB까지 받으므로(infra app-locations.conf) 여유를 두고 24MB로 맞춘다(MR !91
 * CR2-B2). 서버 표본 상한(86,400개)과 별개로, 위치·심박이 많은 긴 러닝이 본문 크기로 413을 받지 않게 한다.
 */
internal const val MAX_UPLOAD_BODY_BYTES = 24 * 1024 * 1024

/** 본문 추정에 쓰는 직렬화. null도 싣는 기본 설정이라 실제 전송 본문보다 크거나 같게 센다. */
private val sizeJson = Json { encodeDefaults = true }

private const val FIT_ROUNDS = 5
private const val FIT_MARGIN = 0.95

/**
 * [toRequest]와 같되, 본문 추정 크기가 [maxBytes]를 넘으면 움직임 표본을 시간 칸으로 더 솎아(thinnedTo) 본문을 줄인다. 위치·심박·정지 구간은
 * 그대로 둔다. 표본을 다 빼도 넘으면 표본 없이 보낸다(서버가 413으로 거부하는 것보다 낫다, MR !91 CR2-B2).
 */
internal fun CompletedRun.toFittedRequest(maxBytes: Int = MAX_UPLOAD_BODY_BYTES): RunUploadRequest {
    var kept = motions
    var request = toRequest()
    repeat(FIT_ROUNDS) {
        val total = bytesOf(sizeJson.encodeToString(RunUploadRequest.serializer(), request))
        if (total <= maxBytes || kept.isEmpty()) return request
        val motionBytes = bytesOf(
            sizeJson.encodeToString(ListSerializer(RunUploadRequest.Motion.serializer()), request.motions)
        )
        val room = (maxBytes - (total - motionBytes)).coerceAtLeast(0).toLong()
        val target = (kept.size * room / motionBytes.coerceAtLeast(1) * FIT_MARGIN).toInt()
        kept = if (target <= 0) emptyList() else kept.thinnedTo(target)
        request = copy(motions = kept).toRequest()
    }
    return request
}

private fun bytesOf(json: String): Int = json.toByteArray(Charsets.UTF_8).size

internal fun HeartRateSample.toRequest(): RunUploadRequest.HeartRate =
    RunUploadRequest.HeartRate(ServerTime.formatWithOffset(at), bpm)

internal fun UploadResponse.toDomain(): UploadReceipt = UploadReceipt(
    recordId = recordId,
    isAlreadySaved =
        status == ALREADY_STORED,
    isValid = valid
)

/** 앱이 모르는 파기 종류는 null(건너뛴다. 확인을 보내지 않으므로 서버가 다음에 다시 준다). */
internal fun DisposalOrderResponse.toDomain(): DisposalOrder? =
    DisposalOrder.Kind.entries.firstOrNull { it.name == kind }?.let { DisposalOrder(id, it) }

/** 서버 RunController.UploadResponse.status: 이미 받은 기록(D-11 멱등). */
private const val ALREADY_STORED = "ALREADY_STORED"
