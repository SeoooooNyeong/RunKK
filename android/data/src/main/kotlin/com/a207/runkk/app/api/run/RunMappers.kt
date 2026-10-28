package com.a207.runkk.app.api.run

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.sync.DisposalOrder
import com.a207.runkk.app.domain.sync.UploadReceipt
import java.time.Duration

// CompletedRun → RunUploadRequest 매퍼(6.1.14 api.run, D-54)와 응답 → 도메인 매퍼.
// elapsedMillis는 운동 시간(정지 제외)이고 endedAt은 시작 + 운동 시간 + 정지 시간 합이다(서버 규칙: 정지 구간은 시작~종료 안, F.2.2).

internal fun CompletedRun.toRequest(): RunUploadRequest = RunUploadRequest(
    recordId = recordId,
    mode = mode.name,
    cardKind = cardKind.name,
    battleId = battleId,
    crewRunPostId = crewRunPostId,
    startedAt = ServerTime.formatWithOffset(startedAt),
    endedAt = ServerTime.formatWithOffset(startedAt.plus(elapsed).plus(pausedTotal())),
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
    }
)

/** 정지 시간 합. */
private fun CompletedRun.pausedTotal(): Duration =
    pauses.fold(Duration.ZERO) { sum, p -> sum.plus(Duration.between(p.from, p.to)) }

internal fun HeartRateSample.toRequest(): RunUploadRequest.HeartRate =
    RunUploadRequest.HeartRate(ServerTime.formatWithOffset(at), bpm)

internal fun UploadResponse.toDomain(): UploadReceipt = UploadReceipt(
    recordId = recordId,
    isAlreadySaved =
        status == ALREADY_STORED
)

/** 앱이 모르는 파기 종류는 null(건너뛴다. 확인을 보내지 않으므로 서버가 다음에 다시 준다). */
internal fun DisposalOrderResponse.toDomain(): DisposalOrder? =
    DisposalOrder.Kind.entries.firstOrNull { it.name == kind }?.let { DisposalOrder(id, it) }

/** 서버 RunController.UploadResponse.status: 이미 받은 기록(D-11 멱등). */
private const val ALREADY_STORED = "ALREADY_STORED"
