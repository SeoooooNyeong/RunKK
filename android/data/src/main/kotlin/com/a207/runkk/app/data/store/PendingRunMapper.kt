package com.a207.runkk.app.data.store

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.PauseInterval
import com.a207.runkk.app.domain.run.RoutePoint
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.UnstableSegment
import com.a207.runkk.app.domain.run.UploadState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// 엔티티 ↔ CompletedRun 매퍼(6.1.14 data.store, 13.2 "도메인 모델과 매퍼").
// 경로·불안정 구간·심박·정지 구간은 [형식 버전 1바이트][개수 Int][항목…]을 GZIP으로 압축한 BLOB이다. 빈 BLOB은 빈 목록이다.
// 시각은 epoch 밀리초, 좌표는 Double 그대로(측정값을 줄이지 않는다), 신호 상태는 0 NORMAL / 1 UNSTABLE.

private const val FORMAT_VERSION = 1
private const val SIGNAL_NORMAL = 0
private const val SIGNAL_UNSTABLE = 1

internal fun CompletedRun.toEntity(): PendingRunEntity = PendingRunEntity(
    recordId = recordId,
    mode = mode.name,
    cardKind = cardKind.name,
    battleId = battleId,
    crewRunPostId = crewRunPostId,
    startedAtMillis = startedAt.toEpochMilli(),
    elapsedMillis = elapsed.toMillis(),
    distanceMeters = distance.meters,
    route = encodeRoute(route),
    unstable = encodeUnstable(unstable),
    heartRates = encodeHeartRates(heartRates),
    uploadState = uploadState.name,
    savedAtMillis = savedAt.toEpochMilli(),
    sentAtMillis = null,
    pauses = encodePauses(pauses),
    startDevice = startDevice.name,
    rejectedReason = rejectedReason
)

internal fun PendingRunEntity.toDomain(): CompletedRun = CompletedRun(
    recordId = recordId,
    mode = RunMode.valueOf(mode),
    cardKind = CardKind.valueOf(cardKind),
    battleId = battleId,
    crewRunPostId = crewRunPostId,
    startedAt = Instant.ofEpochMilli(startedAtMillis),
    elapsed = Duration.ofMillis(elapsedMillis),
    distance = Distance(distanceMeters),
    route = decodeRoute(route),
    unstable = decodeUnstable(unstable),
    heartRates = decodeHeartRates(heartRates),
    pauses = decodePauses(pauses),
    startDevice = RunStartSpec.StartDevice.valueOf(startDevice),
    uploadState = uploadStateOf(uploadState),
    savedAt = Instant.ofEpochMilli(savedAtMillis),
    rejectedReason = rejectedReason
)

internal fun encodeRoute(points: List<RoutePoint>): ByteArray = compress(points.size) { out ->
    points.forEach { p ->
        out.writeLong(p.at.toEpochMilli())
        out.writeDouble(p.point.latitude)
        out.writeDouble(p.point.longitude)
        out.writeByte(if (p.state == SignalState.NORMAL) SIGNAL_NORMAL else SIGNAL_UNSTABLE)
    }
}

internal fun encodeUnstable(segments: List<UnstableSegment>): ByteArray = compress(segments.size) { out ->
    segments.forEach { s ->
        out.writeLong(s.startAt.toEpochMilli())
        out.writeLong(s.endAt.toEpochMilli())
        out.writeLong(s.distance.meters)
    }
}

internal fun encodeHeartRates(samples: List<HeartRateSample>): ByteArray = compress(samples.size) { out ->
    samples.forEach { s ->
        out.writeLong(s.at.toEpochMilli())
        out.writeInt(s.bpm)
    }
}

internal fun encodePauses(pauses: List<PauseInterval>): ByteArray = compress(pauses.size) { out ->
    pauses.forEach { p ->
        out.writeLong(p.from.toEpochMilli())
        out.writeLong(p.to.toEpochMilli())
    }
}

internal fun decodePauses(blob: ByteArray): List<PauseInterval> = decompress(blob) { input ->
    PauseInterval(Instant.ofEpochMilli(input.readLong()), Instant.ofEpochMilli(input.readLong()))
}

internal fun decodeRoute(blob: ByteArray): List<RoutePoint> = decompress(blob) { input ->
    val at = Instant.ofEpochMilli(input.readLong())
    val point = GeoPoint(input.readDouble(), input.readDouble())
    val state = if (input.readByte().toInt() == SIGNAL_NORMAL) SignalState.NORMAL else SignalState.UNSTABLE
    RoutePoint(at, point, state)
}

internal fun decodeUnstable(blob: ByteArray): List<UnstableSegment> = decompress(blob) { input ->
    UnstableSegment(
        startAt = Instant.ofEpochMilli(input.readLong()),
        endAt = Instant.ofEpochMilli(input.readLong()),
        distance = Distance(input.readLong())
    )
}

internal fun decodeHeartRates(blob: ByteArray): List<HeartRateSample> = decompress(blob) { input ->
    HeartRateSample(Instant.ofEpochMilli(input.readLong()), input.readInt())
}

private fun compress(count: Int, write: (DataOutputStream) -> Unit): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(GZIPOutputStream(bytes)).use { out ->
        out.writeByte(FORMAT_VERSION)
        out.writeInt(count)
        write(out)
    }
    return bytes.toByteArray()
}

private fun <T> decompress(blob: ByteArray, read: (DataInputStream) -> T): List<T> {
    if (blob.isEmpty()) return emptyList()
    DataInputStream(GZIPInputStream(ByteArrayInputStream(blob))).use { input ->
        val version = input.readByte().toInt()
        check(version == FORMAT_VERSION) { "알 수 없는 보관 형식 버전: $version" }
        val count = input.readInt()
        return List(count) { read(input) }
    }
}

/** 모르는 전송 상태 문자열(옛 SENDING 등)은 미전송으로 읽는다(R81). 연결되면 다시 보낸다. */
private fun uploadStateOf(name: String): UploadState =
    UploadState.entries.firstOrNull { it.name == name } ?: UploadState.NOT_SENT
