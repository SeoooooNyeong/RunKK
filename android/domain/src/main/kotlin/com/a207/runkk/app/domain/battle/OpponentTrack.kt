package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.TrackTable
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * 기록 상대의 경과 시간별 누적 거리 표(서버 battle.OpponentTrack의 앱 복제, 6.1.6, A-08).
 * 연산·반올림 규칙은 서버와 같고 contract/opponent-track.csv 계약 표로 지킨다(14장).
 * 위치 좌표는 표에 없다(FR-089).
 */
class OpponentTrack private constructor(private val samples: List<Sample>) {
    /** 표본(경과 밀리초, 누적 미터). 경과 시간 오름차순, 누적 거리 단조 증가. */
    private data class Sample(val elapsedMillis: Long, val meters: Double)

    /** 직선 보간(D-13). 첫 표본 전은 (0, 0)부터 비례, 마지막 표본 뒤는 마지막 거리, 음수 경과는 0. */
    fun distanceAt(elapsed: Duration): Distance {
        if (samples.isEmpty()) return Distance.ZERO
        val e = elapsed.toMillis().coerceAtLeast(0).toDouble()
        val first = samples.first()
        val last = samples.last()
        val meters =
            when {
                e <= first.elapsedMillis ->
                    if (first.elapsedMillis == 0L) first.meters else first.meters * (e / first.elapsedMillis)

                e >= last.elapsedMillis -> last.meters

                else -> {
                    val i = indexAtOrBefore(e)
                    val before = samples[i]
                    val after = samples[i + 1]
                    val ratio = (e - before.elapsedMillis) / (after.elapsedMillis - before.elapsedMillis)
                    before.meters + ratio * (after.meters - before.meters)
                }
            }
        return Distance(meters.roundToLong())
    }

    /** d에 처음 닿는 경과 시간(밀리초 반올림). 닿지 않으면 null. */
    fun arrivalTime(d: Distance): Duration? {
        val goal = d.meters.toDouble()
        if (goal <= 0) return Duration.ZERO
        var previous = Sample(0, 0.0)
        for (sample in samples) {
            if (sample.meters >= goal) {
                val span = sample.meters - previous.meters
                val ratio = if (span == 0.0) 1.0 else (goal - previous.meters) / span
                val millis = previous.elapsedMillis + ratio * (sample.elapsedMillis - previous.elapsedMillis)
                return Duration.ofMillis(millis.roundToLong())
            }
            previous = sample
        }
        return null
    }

    /** d 이후 표본을 지우고 (arrivalTime(d), d)를 마지막 표본으로 둔다. d에 닿지 않으면 그대로. */
    fun cut(d: Distance): OpponentTrack {
        val arrival = arrivalTime(d) ?: return this
        val kept = samples.filter { it.meters < d.meters }
        return OpponentTrack(kept + Sample(arrival.toMillis(), d.meters.toDouble()))
    }

    /** samples[i].elapsed ≤ e < samples[i+1].elapsed 인 i(이분 탐색). */
    private fun indexAtOrBefore(e: Double): Int {
        var lo = 0
        var hi = samples.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (samples[mid].elapsedMillis <= e) lo = mid else hi = mid
        }
        return lo
    }

    companion object {
        /** BattleStart.opponentTrack·기록 대결 시작 응답의 표를 바꾼다. */
        fun from(table: TrackTable): OpponentTrack = OpponentTrack(
            table.points
                .sortedBy { it.elapsedSeconds }
                .map { Sample(it.elapsedSeconds * MILLIS_PER_SECOND, it.distanceMeters.toDouble()) }
        )

        /** 균등 페이스 표(1초 간격): t = 1 .. ceil(finish) - 1 에 round(d × t ÷ finish), 마지막은 (finish, d). */
        fun uniform(d: Distance, finish: Duration): OpponentTrack {
            require(!finish.isNegative && !finish.isZero) { "finish time must be positive: $finish" }
            val finishMillis = finish.toMillis()
            val lastWholeSecond = ceil(finishMillis / MILLIS_PER_SECOND.toDouble()).toLong() - 1
            val samples =
                (1..lastWholeSecond).map { t ->
                    val meters = (d.meters * t * MILLIS_PER_SECOND / finishMillis.toDouble()).roundToLong()
                    Sample(t * MILLIS_PER_SECOND, meters.toDouble())
                } + Sample(finishMillis, d.meters.toDouble())
            return OpponentTrack(samples)
        }

        private const val MILLIS_PER_SECOND = 1_000L
    }
}
