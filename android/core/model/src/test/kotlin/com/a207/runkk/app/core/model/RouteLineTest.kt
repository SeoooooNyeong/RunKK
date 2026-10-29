package com.a207.runkk.app.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RouteLine(D-261): 시험 문서 12.3.2 TC-9645. 점은 자오선을 따라 북쪽으로 놓아 하버사인 거리가 미터 값과 같다. */
class RouteLineTest {
    private fun north(meters: Double): GeoPoint = GeoPoint(37.0 + meters / METERS_PER_DEGREE, 127.0)

    private val line = RouteLine(listOf(north(0.0), north(20.0), north(40.0)))

    @Test
    fun `TC-9645 pointAt은 누적 거리로 선분을 보간하고 범위 밖이면 null이다`() {
        assertEquals(40.0, line.lengthMeters, 0.01)
        val middle = assertNotNull(line.pointAt(Distance(10)))
        assertEquals(10.0, north(0.0).metersTo(middle), 0.01, "첫 선분 중간")
        assertEquals(north(40.0).latitude, assertNotNull(line.pointAt(Distance(40))).latitude, 1e-9, "끝점")
        assertNull(line.pointAt(Distance(41)))
        assertNull(line.pointAtMeters(-1.0))
    }

    @Test
    fun `TC-9645 extendedBy는 마지막 20m 방향으로 더 간 점이다`() {
        val ahead = assertNotNull(line.extendedBy(10))
        assertEquals(50.0, north(0.0).metersTo(ahead), 0.05)
        assertEquals(127.0, ahead.longitude, 1e-9)
    }

    @Test
    fun `TC-9645 점 1개짜리 경로는 pointAt·extendedBy가 null이다`() {
        val single = RouteLine(listOf(north(0.0)))
        assertNull(single.pointAt(Distance(0)))
        assertNull(single.extendedBy(10))
        assertNull(RouteLine.EMPTY.pointAt(Distance(0)))
        assertNull(RouteLine.EMPTY.extendedBy(10))
    }

    @Test
    fun `plus는 누적 거리를 이어 계산하고 bounds는 모든 점을 담는다`() {
        val grown = RouteLine.EMPTY + north(0.0) + north(20.0) + north(40.0)
        assertEquals(line.lengthMeters, grown.lengthMeters, 1e-6)
        assertEquals(3, grown.points.size)
        val bounds = assertNotNull(grown.bounds())
        assertNull(RouteLine.EMPTY.bounds())
        assertEquals(north(0.0).latitude, bounds.minLatitude, 1e-9)
        assertEquals(north(40.0).latitude, bounds.maxLatitude, 1e-9)
        assertTrue(bounds.minLongitude <= bounds.maxLongitude)
    }

    @Test
    fun `꺾인 경로의 extendedBy는 마지막 20m 지점에서 끝점으로 가는 방향이다`() {
        // 북쪽 100m 뒤 동쪽으로 30m 꺾는다. 마지막 20m는 동쪽 선분 위에 있다.
        val east = GeoPoint(
            north(100.0).latitude,
            127.0 + 30.0 / (METERS_PER_DEGREE * Math.cos(Math.toRadians(37.0009)))
        )
        val bent = RouteLine(listOf(north(0.0), north(100.0), east))
        val ahead = assertNotNull(bent.extendedBy(20))
        assertEquals(east.latitude, ahead.latitude, 1e-6)
        assertTrue(ahead.longitude > east.longitude)
    }

    private companion object {
        const val METERS_PER_DEGREE = 6_371_000.0 * Math.PI / 180.0
    }
}
