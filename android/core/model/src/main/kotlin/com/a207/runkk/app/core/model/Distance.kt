package com.a207.runkk.app.core.model

import kotlin.math.roundToLong

/** 거리 하나를 미터 단위로 표현한다(서버 common.Distance와 같은 규칙, 6.1.1). */
@JvmInline
value class Distance(val meters: Long) : Comparable<Distance> {
    init {
        require(meters >= 0) { "거리는 0 이상이어야 한다: $meters" }
    }

    fun plus(d: Distance): Distance = Distance(meters + d.meters)

    /** 결과가 음수면 ZERO. */
    fun minus(d: Distance): Distance = Distance((meters - d.meters).coerceAtLeast(0))

    fun isAtLeast(d: Distance): Boolean = meters >= d.meters

    /** km 환산, 소수 둘째 자리 반올림. */
    fun km(): Double = (meters / 10.0).roundToLong() / 100.0

    override fun compareTo(other: Distance): Int = meters.compareTo(other.meters)

    companion object {
        val ZERO = Distance(0)

        fun ofMeters(m: Long): Distance = Distance(m)

        fun ofKm(km: Double): Distance = Distance((km * 1_000).roundToLong())
    }
}
