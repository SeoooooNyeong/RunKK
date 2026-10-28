package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/** 누적 거리가 간격의 새 지점(1, 2, …번째)을 지났는지 센다. 여러 지점을 한 번에 넘으면 가장 큰 지점 하나만 알린다. */
internal class PointCrossing(val intervalMeters: Long, startAt: Distance = Distance.ZERO) {
    private var lastPoint = startAt.meters / intervalMeters

    fun newPoint(distance: Distance): Long? {
        val point = distance.meters / intervalMeters
        if (point <= lastPoint) return null
        lastPoint = point
        return point
    }
}

/** 1km(구간·순위 안내 간격). */
internal const val KILOMETER = 1_000L
