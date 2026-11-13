package com.a207.runkk.app.domain.run

import java.util.TreeMap

/** GPS 좌표가 늦게 도착해도 같은 부팅 시각의 IMU 상태를 적용한다. */
class InertialMotionHistory {
    private val states = TreeMap<Long, InertialMotion>()

    @Synchronized
    fun record(at: Long, motion: InertialMotion) {
        val last = states.lastEntry()
        if (last != null && (at <= last.key || (motion == last.value && at - last.key < RECORD_INTERVAL_MS))) return
        states[at] = motion
        states.headMap(at - HISTORY_MS - MAX_SENSOR_AGE_MS).clear()
    }

    @Synchronized
    fun stateAt(at: Long, now: Long): InertialMotion {
        if (now - at !in 0..HISTORY_MS) return InertialMotion.UNKNOWN
        val state = states.floorEntry(at) ?: return InertialMotion.UNKNOWN
        return if (at - state.key <= MAX_SENSOR_AGE_MS) state.value else InertialMotion.UNKNOWN
    }

    @Synchronized
    fun clear() = states.clear()

    private companion object {
        const val HISTORY_MS = 5 * 60_000L
        const val RECORD_INTERVAL_MS = 100L
        const val MAX_SENSOR_AGE_MS = 600L
    }
}
