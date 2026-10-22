package com.a207.runkk.app.core.model

import java.time.Duration
import kotlin.math.roundToInt

/** 1km를 달리는 데 걸린 시간(초)을 표현한다(서버 common.Pace와 같은 규칙, 6.1.1). */
@JvmInline
value class Pace(val secondsPerKm: Int) {
    init {
        require(secondsPerKm > 0) { "페이스는 0보다 커야 한다: $secondsPerKm" }
    }

    /** 목표보다 ratio 이상 느린가: secondsPerKm ≥ target × (1 + ratio). FR-044는 0.1. */
    fun isSlowerThanBy(target: Pace, ratio: Double): Boolean {
        require(ratio >= 0) { "ratio는 0 이상이어야 한다: $ratio" }
        return secondsPerKm >= target.secondsPerKm * (1 + ratio)
    }

    fun isWithin(fastest: Pace, slowest: Pace): Boolean = secondsPerKm in fastest.secondsPerKm..slowest.secondsPerKm

    fun speedKmh(): Double = 3_600.0 / secondsPerKm

    companion object {
        /** 경과 시간 ÷ 거리. 거리가 0이면 IllegalArgumentException. */
        fun of(elapsed: Duration, distance: Distance): Pace {
            require(distance.meters > 0) { "거리 0으로 페이스를 만들 수 없다" }
            val seconds = elapsed.toMillis() / 1_000.0
            return Pace((seconds * 1_000 / distance.meters).roundToInt().coerceAtLeast(1))
        }
    }
}
