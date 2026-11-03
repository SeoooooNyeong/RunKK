package com.a207.runkk.watch.cache

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.battle.RecordTrack
import com.a207.runkk.app.domain.live.TrackPoint
import com.a207.runkk.app.domain.live.TrackTable
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlinx.serialization.Serializable

/**
 * OpponentRecordCache의 파일 형식(JSON 한 개, 회원ID 머리, D-377). 진행표는 1초마다의 누적 미터로 둔다: OpponentTrack은 표본을 밖에
 * 내지 않으므로 distanceAt으로 1초마다 읽어 두고, 읽을 때 같은 1초 간격 TrackTable로 다시 만든다(서버 표는 정수 초라 값이 같다).
 * 아직 구간 기록을 받지 않은 셀럽 목록 항목(W31, CachedTrack.hasTrack 거짓)은 진행표가 빈 목록이다.
 */
@Serializable
internal data class CachedRecords(
    val memberId: Long,
    val tracks: List<Entry>,
    /** 셀프 목록을 받은 적 있음(W32). 옛 파일에는 없고, 없으면 거짓이다. */
    val isSelfReceived: Boolean = false
) {
    @Serializable
    data class Entry(
        val kind: String,
        val distanceMeters: Long,
        val celebrityId: Long? = null,
        val displayName: String,
        val finishMillis: Long,
        val sourceLabel: String? = null,
        val celebrityFigure: String? = null,
        /** 경과 1초, 2초, …의 누적 미터. 마지막 값이 도착 거리다. 구간 기록을 받지 않은 셀럽은 비어 있다(W31). */
        val metersPerSecond: List<Long>,
        val fetchedAtEpochMillis: Long
    ) {
        fun toCached(): CachedTrack = CachedTrack(
            RecordTrack(
                kind = RecordBattleKind.valueOf(kind),
                distance = Distance(distanceMeters),
                celebrityId = celebrityId,
                displayName = displayName,
                finishTime = Duration.ofMillis(finishMillis),
                sourceLabel = sourceLabel,
                celebrityFigure = celebrityFigure,
                track = OpponentTrack.from(
                    TrackTable(metersPerSecond.mapIndexed { index, meters -> TrackPoint(index + 1L, meters) })
                )
            ),
            Instant.ofEpochMilli(fetchedAtEpochMillis)
        )

        companion object {
            fun of(cached: CachedTrack): Entry {
                val record = cached.record
                return Entry(
                    kind = record.kind.name,
                    distanceMeters = record.distance.meters,
                    celebrityId = record.celebrityId,
                    displayName = record.displayName,
                    finishMillis = record.finishTime.toMillis(),
                    sourceLabel = record.sourceLabel,
                    celebrityFigure = record.celebrityFigure,
                    metersPerSecond = if (cached.hasTrack) metersPerSecond(record) else emptyList(),
                    fetchedAtEpochMillis = cached.fetchedAt.toEpochMilli()
                )
            }

            private fun metersPerSecond(record: RecordTrack): List<Long> {
                val arrival = record.track.arrivalTime(record.distance) ?: record.finishTime
                val lastSecond = ceil(maxOf(arrival, record.finishTime).toMillis() / MILLIS_PER_SECOND).toLong()
                return (1..lastSecond).map { record.track.distanceAt(Duration.ofSeconds(it)).meters }
            }

            private const val MILLIS_PER_SECOND = 1_000.0
        }
    }
}
