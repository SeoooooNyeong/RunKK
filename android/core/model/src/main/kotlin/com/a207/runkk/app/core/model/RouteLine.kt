package com.a207.runkk.app.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * 측정 중 경로선(D-261). 점마다 처음부터의 누적 거리(하버사인, 미터)를 생성 때 한 번 계산해 두고, 누적 거리로 선 위 지점을 찾는다.
 * ui.run·ui.battle(측정 화면 RouteCanvas)과 상대 마커 위치(OpponentMarkerPlacer)가 같은 계산을 쓴다.
 * 선분 안 보간은 위도·경도 선형 보간이다(1초 간격 포인트 사이는 수 m라 오차가 없다).
 */
class RouteLine private constructor(val points: List<GeoPoint>, private val cumulative: DoubleArray) {
    constructor(points: List<GeoPoint>) : this(points, cumulativeOf(points))

    /** 경로 전체 길이(m). 점이 하나 이하면 0이다. */
    val lengthMeters: Double
        get() = cumulative.lastOrNull() ?: 0.0

    /** 점 하나를 끝에 더한 새 경로. 앞 점들의 누적 거리는 다시 계산하지 않는다. */
    operator fun plus(point: GeoPoint): RouteLine {
        val last = points.lastOrNull()
        val next = cumulative.copyOf(cumulative.size + 1)
        next[cumulative.size] = if (last == null) 0.0 else lengthMeters + last.metersTo(point)
        return RouteLine(points + point, next)
    }

    /** 출발점에서 [distance]만큼 간 선 위 지점. 경로가 선분 하나도 없거나 [distance]가 경로 길이를 넘으면 null이다. */
    fun pointAt(distance: Distance): GeoPoint? = pointAtMeters(distance.meters.toDouble())

    /** [pointAt]의 미터 값 판. 음수면 null이다. */
    fun pointAtMeters(meters: Double): GeoPoint? {
        if (points.size < 2 || meters < 0.0 || meters > lengthMeters + EPSILON_METERS) return null
        val target = min(meters, lengthMeters)
        var i = 1
        while (i < cumulative.size - 1 && cumulative[i] < target) i++
        val from = points[i - 1]
        val to = points[i]
        val span = cumulative[i] - cumulative[i - 1]
        val ratio = if (span <= 0.0) 1.0 else ((target - cumulative[i - 1]) / span).coerceIn(0.0, 1.0)
        return lerp(from, to, ratio)
    }

    /**
     * 경로 끝에서 진행 방향으로 [meters]만큼 더 간 지점(앞선 상대 마커, D-262). 방향은 마지막 [DIRECTION_METERS] 지점에서 끝점으로
     * 가는 직선이다. 경로가 그보다 짧거나 그 두 점이 겹치면(제자리 왕복) null이다.
     */
    fun extendedBy(meters: Int): GeoPoint? {
        if (points.size < 2 || !isLongEnoughForDirection) return null
        val from = pointAtMeters((lengthMeters - DIRECTION_METERS).coerceAtLeast(0.0)) ?: return null
        val to = points.last()
        val span = from.metersTo(to)
        if (span <= 0.0) return null
        val ratio = 1.0 + meters / span
        return lerp(from, to, ratio)
    }

    /** 진행 방향을 잡을 만큼 긴가([DIRECTION_METERS] 이상, 계산 오차 1mm 허용). */
    val isLongEnoughForDirection: Boolean
        get() = lengthMeters + LENGTH_TOLERANCE_METERS >= DIRECTION_METERS

    /** 모든 점을 담는 위도·경도 범위. 점이 없으면 null이다. */
    fun bounds(): GeoBounds? {
        if (points.isEmpty()) return null
        var minLat = points[0].latitude
        var maxLat = minLat
        var minLon = points[0].longitude
        var maxLon = minLon
        for (p in points) {
            minLat = min(minLat, p.latitude)
            maxLat = max(maxLat, p.latitude)
            minLon = min(minLon, p.longitude)
            maxLon = max(maxLon, p.longitude)
        }
        return GeoBounds(minLat, minLon, maxLat, maxLon)
    }

    override fun equals(other: Any?): Boolean = other is RouteLine && other.points == points

    override fun hashCode(): Int = points.hashCode()

    override fun toString(): String = "RouteLine(points=${points.size}, length=${lengthMeters.toLong()}m)"

    companion object {
        /** 점 없는 경로(측정 전). */
        val EMPTY = RouteLine(emptyList(), DoubleArray(0))

        /** 앞선 상대 마커의 방향을 잡는 마지막 구간 길이(m). 이보다 짧은 경로는 상대 마커를 그리지 않는다(D-262). */
        const val DIRECTION_METERS = 20.0

        private const val EPSILON_METERS = 1e-6
        private const val LENGTH_TOLERANCE_METERS = 1e-3

        private fun cumulativeOf(points: List<GeoPoint>): DoubleArray {
            val sums = DoubleArray(points.size)
            for (i in 1 until points.size) sums[i] = sums[i - 1] + points[i - 1].metersTo(points[i])
            return sums
        }

        private fun lerp(from: GeoPoint, to: GeoPoint, ratio: Double): GeoPoint = GeoPoint(
            (from.latitude + (to.latitude - from.latitude) * ratio).coerceIn(-90.0, 90.0),
            (from.longitude + (to.longitude - from.longitude) * ratio).coerceIn(-180.0, 180.0)
        )
    }
}

/** 위도·경도 범위(RouteLine.bounds). RouteCanvas가 이 범위를 화면에 맞춘다. */
data class GeoBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double
)
