package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.GeoPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RouteCanvas 등거리 투영(D-261): 범위를 화면 안에 비율대로 넣고, 짧은 경로는 최소 범위로 본다. */
class RouteProjectionTest {
    private val start = GeoPoint(37.5, 127.0)

    private fun north(meters: Double) = GeoPoint(37.5 + meters / 111_320.0, 127.0)

    private fun east(meters: Double) = GeoPoint(37.5, 127.0 + meters / (111_320.0 * Math.cos(Math.toRadians(37.5))))

    @Test
    fun `경로 끝점은 여백 안에 들어가고 남북 1km와 동서 1km의 화면 길이가 같다`() {
        val points = listOf(start, north(1_000.0), east(1_000.0))
        val p = RouteProjection.fit(points, width = 400f, height = 400f, padding = 20f)
        points.map(p::project).forEach {
            assertTrue(it.x in 19f..381f && it.y in 19f..381f, "$it")
        }
        val up = p.project(start).y - p.project(north(1_000.0)).y
        val right = p.project(east(1_000.0)).x - p.project(start).x
        assertTrue(abs(up - right) < 1f, "up=$up right=$right")
    }

    @Test
    fun `점 하나·짧은 경로는 가운데에 놓이고 최소 범위로 확대를 막는다`() {
        val single = RouteProjection.fit(listOf(start), width = 300f, height = 600f, padding = 0f)
        assertEquals(150f, single.project(start).x, 0.01f)
        assertEquals(300f, single.project(start).y, 0.01f)
        val short = RouteProjection.fit(listOf(start, north(10.0)), width = 300f, height = 600f, padding = 0f)
        val length = short.project(start).y - short.project(north(10.0)).y
        assertTrue(length <= 300f * 10 / RouteProjection.MIN_SPAN_METERS + 1, "10m가 화면 전체로 퍼지지 않는다: $length")
    }

    @Test
    fun `점이 없어도 예외 없이 가운데를 낸다`() {
        val empty = RouteProjection.fit(emptyList(), width = 100f, height = 100f, padding = 10f)
        assertEquals(50f, empty.project(GeoPoint(0.0, 0.0)).x, 0.01f)
    }
}
