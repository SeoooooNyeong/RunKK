package com.a207.runkk.app.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * 측정 중 경로선(D-261). 점마다 처음부터의 누적 거리(하버사인, 미터)를 한 번 계산해 두고, 누적 거리로 선 위 지점을 찾는다.
 * ui.run·ui.battle(측정 화면 RouteCanvas)과 상대 마커 위치(OpponentMarkerPlacer)가 같은 계산을 쓴다.
 * 선분 안 보간은 위도·경도 선형 보간이다(1초 간격 포인트 사이는 수 m라 오차가 없다).
 *
 * 측정 중에는 1초마다 점이 하나씩 붙는다. [plus]는 마지막 경로에서 부르면 같은 버퍼 끝에 덧붙이고 앞 [size]개만 보는 새 값을 내므로
 * 점마다 전체를 복사하지 않는다(값은 불변으로 보인다: 앞 점들은 바뀌지 않는다). 범위(bounds)도 덧붙일 때 갱신해 둔다.
 */
class RouteLine private constructor(private val buffer: Buffer, val size: Int) {
    constructor(points: List<GeoPoint>) : this(Buffer().also { b -> points.forEach(b::append) }, points.size)

    /** 점 목록(읽기 전용 보기). */
    val points: List<GeoPoint> = object : AbstractList<GeoPoint>() {
        override val size: Int get() = this@RouteLine.size

        override fun get(index: Int): GeoPoint {
            if (index !in 0 until size) throw IndexOutOfBoundsException("index $index, size $size")
            return buffer.pointAt(index)
        }
    }

    private val bounds: GeoBounds? = if (size == 0) null else buffer.boundsAt(size - 1)

    /** 경로 전체 길이(m). 점이 하나 이하면 0이다. */
    val lengthMeters: Double
        get() = if (size == 0) 0.0 else buffer.cumulativeAt(size - 1)

    /** 점 하나를 끝에 더한 새 경로. 이 값이 버퍼의 마지막이면 덧붙이고, 아니면(갈라진 경로) 앞 점들을 복사한다. */
    operator fun plus(point: GeoPoint): RouteLine = synchronized(buffer) {
        // 빈 경로(EMPTY 등 함께 쓰는 값)에서는 새 버퍼로 시작해 다른 측정의 점이 섞이지 않게 한다.
        if (size == 0) {
            RouteLine(listOf(point))
        } else if (buffer.count == size) {
            buffer.append(point)
            RouteLine(buffer, size + 1)
        } else {
            RouteLine(points + point)
        }
    }

    /** 출발점에서 [distance]만큼 간 선 위 지점. 경로가 선분 하나도 없거나 [distance]가 경로 길이를 넘으면 null이다. */
    fun pointAt(distance: Distance): GeoPoint? = pointAtMeters(distance.meters.toDouble())

    /** [pointAt]의 미터 값 판. 음수면 null이다. */
    fun pointAtMeters(meters: Double): GeoPoint? {
        if (size < 2 || meters < 0.0 || meters > lengthMeters + EPSILON_METERS) return null
        val target = min(meters, lengthMeters)
        // 누적 거리는 오름차순이라 이분 탐색으로 target 이상인 첫 점을 찾는다.
        var lo = 1
        var hi = size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (buffer.cumulativeAt(mid) < target) lo = mid + 1 else hi = mid
        }
        val from = buffer.pointAt(lo - 1)
        val to = buffer.pointAt(lo)
        val start = buffer.cumulativeAt(lo - 1)
        val span = buffer.cumulativeAt(lo) - start
        val ratio = if (span <= 0.0) 1.0 else ((target - start) / span).coerceIn(0.0, 1.0)
        return lerp(from, to, ratio)
    }

    /**
     * 경로 끝에서 진행 방향으로 [meters]만큼 더 간 지점(앞선 상대 마커, D-262). 방향은 마지막 [DIRECTION_METERS] 지점에서 끝점으로
     * 가는 직선이다. 경로가 그보다 짧거나 그 두 점이 겹치면(제자리 왕복) null이다.
     */
    fun extendedBy(meters: Int): GeoPoint? {
        if (size < 2 || !isLongEnoughForDirection) return null
        val from = pointAtMeters((lengthMeters - DIRECTION_METERS).coerceAtLeast(0.0)) ?: return null
        val to = buffer.pointAt(size - 1)
        val span = from.metersTo(to)
        if (span <= 0.0) return null
        val ratio = 1.0 + meters / span
        return lerp(from, to, ratio)
    }

    /** 진행 방향을 잡을 만큼 긴가([DIRECTION_METERS] 이상, 계산 오차 1mm 허용). */
    val isLongEnoughForDirection: Boolean
        get() = lengthMeters + LENGTH_TOLERANCE_METERS >= DIRECTION_METERS

    /** 모든 점을 담는 위도·경도 범위. 점이 없으면 null이다. 덧붙일 때 계산해 둔 값이라 점 수와 관계없이 바로 낸다. */
    fun bounds(): GeoBounds? = bounds

    /** 같은 버퍼의 같은 길이면 바로 같다고 본다(측정 중 StateFlow 비교가 점 전체를 훑지 않게). */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RouteLine || other.size != size) return false
        if (other.buffer === buffer) return true
        return other.points == points
    }

    override fun hashCode(): Int = if (size == 0) 0 else 31 * size + buffer.pointAt(size - 1).hashCode()

    override fun toString(): String = "RouteLine(points=$size, length=${lengthMeters.toLong()}m)"

    /**
     * 덧붙이기만 하는 점·누적 거리·범위 저장소. 쓰기는 RouteLine.plus가 synchronized로 하고, 읽기는 자기 size 앞쪽만 읽는다.
     * 배열을 늘릴 때는 새 배열에 다 옮긴 뒤 바꿔 끼우므로(@Volatile) 읽는 쪽은 옛 배열이든 새 배열이든 같은 앞쪽 값을 본다.
     */
    private class Buffer {
        @Volatile private var points: Array<GeoPoint?> = arrayOfNulls(INITIAL_CAPACITY)

        @Volatile private var cumulative: DoubleArray = DoubleArray(INITIAL_CAPACITY)

        @Volatile private var boundsList: Array<GeoBounds?> = arrayOfNulls(INITIAL_CAPACITY)

        @Volatile var count: Int = 0
            private set

        fun pointAt(i: Int): GeoPoint = requireNotNull(points[i])

        fun cumulativeAt(i: Int): Double = cumulative[i]

        fun boundsAt(i: Int): GeoBounds? = boundsList[i]

        fun append(p: GeoPoint) {
            val n = count
            if (n == points.size) grow(n * 2)
            val previous = if (n == 0) null else points[n - 1]
            val previousBounds = if (n == 0) null else boundsList[n - 1]
            points[n] = p
            cumulative[n] = if (previous == null) 0.0 else cumulative[n - 1] + previous.metersTo(p)
            boundsList[n] = previousBounds?.including(p) ?: GeoBounds(p.latitude, p.longitude, p.latitude, p.longitude)
            count = n + 1
        }

        private fun grow(capacity: Int) {
            val newPoints = points.copyOf(capacity)
            val newCumulative = cumulative.copyOf(capacity)
            val newBounds = boundsList.copyOf(capacity)
            points = newPoints
            cumulative = newCumulative
            boundsList = newBounds
        }

        private fun GeoBounds.including(p: GeoPoint): GeoBounds = GeoBounds(
            min(minLatitude, p.latitude),
            min(minLongitude, p.longitude),
            max(maxLatitude, p.latitude),
            max(maxLongitude, p.longitude)
        )
    }

    companion object {
        /** 점 없는 경로(측정 전). */
        val EMPTY = RouteLine(emptyList())

        /** 앞선 상대 마커의 방향을 잡는 마지막 구간 길이(m). 이보다 짧은 경로는 상대 마커를 그리지 않는다(D-262). */
        const val DIRECTION_METERS = 20.0

        private const val EPSILON_METERS = 1e-6
        private const val LENGTH_TOLERANCE_METERS = 1e-3
        private const val INITIAL_CAPACITY = 64

        private fun lerp(from: GeoPoint, to: GeoPoint, ratio: Double): GeoPoint = GeoPoint(
            (from.latitude + (to.latitude - from.latitude) * ratio).coerceIn(-90.0, 90.0),
            (from.longitude + (to.longitude - from.longitude) * ratio).coerceIn(-180.0, 180.0)
        )
    }
}
