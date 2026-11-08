package com.a207.runkk.app.domain.run

import java.time.Duration
import java.time.Instant

/**
 * 누적 걸음 수로 케이던스(분당 걸음 수)를 셈한다(S15P31A207-242). 폰 걸음 센서(TYPE_STEP_COUNTER)와, STEPS_PER_MINUTE를 지원하지 않는
 * 워치의 Health Services STEPS가 쓴다. 최근 [WINDOW] 동안 늘어난 걸음 수를 그 시간으로 나눈다. 창이 [MIN_SPAN]보다 짧으면 값을 내지
 * 않고, 값은 [EMIT_EVERY]에 한 번만 낸다. 누적 값이 줄면(센서 재시작) 처음부터 다시 센다. 러닝 한 회마다 새로 만든다.
 */
class CadenceMeter {
    private val readings = ArrayDeque<Pair<Instant, Long>>()
    private var lastEmitted: Instant? = null

    /** 누적 걸음 수 하나를 받는다. 낼 값이 있으면 케이던스(분당 걸음 수), 없으면 null. */
    fun accept(at: Instant, cumulativeSteps: Long): Double? {
        val last = readings.lastOrNull()
        if (last != null && (cumulativeSteps < last.second || at.isBefore(last.first))) {
            readings.clear()
            lastEmitted = null
        }
        readings += at to cumulativeSteps
        val windowStart = at.minus(WINDOW)
        // 창 시작보다 앞선 값은 창 시작 직전 하나만 남긴다(창 전체를 덮는 기준점).
        while (readings.size > 2 && !readings[1].first.isAfter(windowStart)) {
            readings.removeFirst()
        }
        val (fromAt, fromSteps) = readings.first()
        val span = Duration.between(fromAt, at)
        if (span < MIN_SPAN) return null
        val previous = lastEmitted
        if (previous != null && Duration.between(previous, at) < EMIT_EVERY) return null
        lastEmitted = at
        return (cumulativeSteps - fromSteps) * MILLIS_PER_MINUTE / span.toMillis()
    }

    companion object {
        /** 케이던스를 셈하는 창. */
        val WINDOW: Duration = Duration.ofSeconds(10)

        /** 이보다 짧은 창으로는 셈하지 않는다(첫 걸음 몇 개로 튀는 값을 막는다). */
        val MIN_SPAN: Duration = Duration.ofSeconds(5)

        /** 값을 내는 최소 간격(센서가 걸음마다 알려도 1초에 한 번). */
        val EMIT_EVERY: Duration = Duration.ofSeconds(1)

        private const val MILLIS_PER_MINUTE = 60_000.0
    }
}
