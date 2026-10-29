package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.RouteLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 상대 마커 위치(D-262): 시험 문서 12.3.2 TC-9639~TC-9644. 경로는 자오선을 따라 북쪽으로 간 직선이다. */
class OpponentMarkerPlacerTest {
    private fun north(meters: Double): GeoPoint = GeoPoint(37.0 + meters / METERS_PER_DEGREE, 127.0)

    private fun straight(lengthMeters: Int): RouteLine = RouteLine(
        (0..lengthMeters step 10).map {
            north(it.toDouble())
        } +
            listOfNotNull(north(lengthMeters.toDouble()).takeIf { lengthMeters % 10 != 0 })
    )

    private fun metersFromStart(p: GeoPoint): Double = north(0.0).metersTo(p) * if (p.latitude < 37.0) -1 else 1

    @Test
    fun `TC-9639 상대가 뒤면 내 거리에서 격차만큼 뺀 경로 지점이다`() {
        val at = assertNotNull(OpponentMarkerPlacer.place(straight(150), Distance(150), -30))
        assertEquals(120.0, metersFromStart(at), 0.05)
    }

    @Test
    fun `TC-9640 상대가 앞이면 경로 끝에서 마지막 20m 방향으로 연장한다`() {
        val at = assertNotNull(OpponentMarkerPlacer.place(straight(150), Distance(150), 42))
        assertEquals(192.0, metersFromStart(at), 0.05)
    }

    @Test
    fun `TC-9641 격차 0이면 경로 끝(내 위치)이다`() {
        val at = assertNotNull(OpponentMarkerPlacer.place(straight(150), Distance(150), 0))
        assertEquals(150.0, metersFromStart(at), 0.05)
    }

    @Test
    fun `TC-9642 경로가 20m 미만이면 그리지 않고 20m부터 그린다`() {
        assertNull(OpponentMarkerPlacer.place(straight(19), Distance(19), -5))
        assertNotNull(OpponentMarkerPlacer.place(straight(20), Distance(20), -5))
    }

    @Test
    fun `TC-9643 격차를 모르면(연결 확인 중) 숨긴다`() {
        assertNull(OpponentMarkerPlacer.place(straight(150), Distance(150), null))
    }

    @Test
    fun `TC-9644 상대가 내 출발점보다 뒤면 그릴 곳이 없어 숨긴다`() {
        assertNull(OpponentMarkerPlacer.place(straight(50), Distance(50), -118))
    }

    @Test
    fun `내 측정 거리가 경로 길이보다 길어도 경로 끝 기준으로 놓는다`() {
        val at = assertNotNull(OpponentMarkerPlacer.place(straight(150), Distance(180), -30))
        assertEquals(120.0, metersFromStart(at), 0.05)
    }

    private companion object {
        const val METERS_PER_DEGREE = 6_371_000.0 * Math.PI / 180.0
    }
}
