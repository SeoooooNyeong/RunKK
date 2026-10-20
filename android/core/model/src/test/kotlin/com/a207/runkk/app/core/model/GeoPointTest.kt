package com.a207.runkk.app.core.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeoPointTest {
    @Test
    fun `위도·경도 범위를 검사한다`() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, -181.0) }
    }

    @Test
    fun `하버사인 거리 - 위도 0·001도는 약 111m`() {
        val a = GeoPoint(37.5, 127.0)
        val b = GeoPoint(37.501, 127.0)
        assertTrue(abs(a.metersTo(b) - 111.19) < 0.1)
        assertEquals(Distance(111), a.distanceTo(b))
    }

    @Test
    fun `같은 점은 0m`() {
        val a = GeoPoint(37.5, 127.0)
        assertEquals(Distance.ZERO, a.distanceTo(a))
    }

    @Test
    fun `소수 자릿수를 낮춘다 (NFR-038)`() {
        assertEquals(GeoPoint(37.57, 126.98), GeoPoint(37.5665, 126.9780).coarsen(2))
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, 0.0).coarsen(7) }
    }
}
