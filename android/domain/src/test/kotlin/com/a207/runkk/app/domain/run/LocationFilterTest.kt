package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.SignalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A-02 LocationFilter.accept: 반경 30m, 저정확도 10초(srs.md 6.3 2.1, FR-032·FR-033). */
class LocationFilterTest {
    private val filter = LocationFilter()

    @Test
    fun `반경 30m 초과는 버린다`() {
        assertNull(filter.accept(sample(0, 0.0, accuracy = 30.1)))
    }

    @Test
    fun `반경 30m 이하는 NORMAL`() {
        assertEquals(SignalState.NORMAL, filter.accept(sample(0, 0.0, accuracy = 30.0))?.state)
    }

    @Test
    fun `저정확도가 10초 미만이었으면 다음 포인트는 NORMAL`() {
        filter.accept(sample(0, 0.0, accuracy = 50.0))
        filter.accept(sample(5, 0.0, accuracy = 50.0))
        assertEquals(SignalState.NORMAL, filter.accept(sample(9, 10.0))?.state)
    }

    @Test
    fun `저정확도가 10초 이상이었으면 다음 포인트는 UNSTABLE이고 그 뒤는 다시 NORMAL`() {
        filter.accept(sample(0, 0.0, accuracy = 50.0))
        assertEquals(SignalState.UNSTABLE, filter.accept(sample(10, 30.0))?.state)
        assertEquals(SignalState.NORMAL, filter.accept(sample(11, 33.0))?.state)
    }

    @Test
    fun `FilteredPoint는 측정 일시와 좌표를 그대로 담는다`() {
        val s = sample(3, 12.0)
        val p = filter.accept(s)
        assertEquals(s.at, p?.at)
        assertEquals(s.point, p?.point)
    }

    @Test
    fun `저속이 확실하면 이어지는 좌표 밀림을 보류하고 실제 이동을 이어 받는다`() {
        val freeRun = LocationFilter(isStabilizationEnabled = true)
        freeRun.accept(sample(0, 0.0))
        for (second in 1L..12L) {
            assertNull(freeRun.accept(sample(second, second * 3.0).copy(speedMps = 0.1, speedAccuracyMps = 0.2)))
        }
        val moving = sample(13, 2.0).copy(speedMps = 1.5, speedAccuracyMps = 0.2)
        assertEquals(SignalState.NORMAL, freeRun.accept(moving)?.state)
    }

    @Test
    fun `튄 점은 다음 위치의 기준이 되지 않는다`() {
        val freeRun = LocationFilter(isStabilizationEnabled = true)
        freeRun.accept(sample(0, 0.0))
        assertNull(freeRun.accept(sample(1, 45.0)))
        val next = sample(2, 6.0)
        assertTrue(checkNotNull(freeRun.accept(next)).point.metersTo(next.point) < 3.0)
    }

    @Test
    fun `칼만 필터는 약한 지그재그를 줄이면서 직선 이동을 따라간다`() {
        val filter = LocationFilter(isStabilizationEnabled = true)
        var previous = checkNotNull(filter.accept(sample(0, 0.0))).point
        var distance = 0.0
        for (second in 1L..50L) {
            val raw = sample(second, second * 2.0)
            val point = raw.point.copy(longitude = raw.point.longitude + if (second % 2 == 0L) 0.000018 else -0.000018)
            val accepted = checkNotNull(filter.accept(raw.copy(point = point))).point
            distance += previous.metersTo(accepted)
            previous = accepted
        }
        assertTrue(distance in 95.0..125.0, "distance=$distance")
        assertTrue(previous.metersTo(north(100.0)) < 3.0)
    }

    @Test
    fun `이동 중 정확도 범위의 흔들림은 보정하고 큰 순간이동은 버린다`() {
        val filter = LocationFilter(isStabilizationEnabled = true)
        fun moving(second: Long, meters: Double) = sample(second, meters).copy(
            speedMps = 3.0,
            speedAccuracyMps = 0.3,
            inertialMotion = InertialMotion.MOVING
        )
        filter.accept(moving(0, 0.0))
        filter.accept(moving(1, 3.0))
        val previous = checkNotNull(filter.accept(moving(2, 6.0))).point
        val next = checkNotNull(filter.accept(moving(3, 13.5))).point
        assertTrue(previous.metersTo(next) in 1.0..6.9)
        assertNull(filter.accept(moving(4, 60.0)))
        assertEquals(SignalState.NORMAL, filter.accept(moving(5, 15.0))?.state)
    }

    @Test
    fun `위치가 오래 끊기면 연속된 두 좌표를 확인하고 거리 없이 기준을 옮긴다`() {
        val freeRun = LocationFilter(isStabilizationEnabled = true)
        freeRun.accept(sample(0, 0.0))
        assertNull(freeRun.accept(sample(12, 110.0)))
        assertEquals(SignalState.UNSTABLE, freeRun.accept(sample(13, 113.0))?.state)
        assertEquals(SignalState.NORMAL, freeRun.accept(sample(14, 116.0))?.state)
    }

    @Test
    fun `정지를 늦게 풀어도 두 이동 좌표가 맞으면 기준점을 새로 잡는다`() {
        val filter = LocationFilter(isStabilizationEnabled = true)
        filter.accept(sample(0, 0.0))
        for (second in 1L..4L) {
            assertNull(filter.accept(sample(second, second * 4.0).copy(inertialMotion = InertialMotion.STILL)))
        }
        assertNull(filter.accept(sample(5, 20.0).copy(inertialMotion = InertialMotion.MOVING)))
        assertEquals(
            SignalState.UNSTABLE,
            filter.accept(sample(6, 24.0).copy(inertialMotion = InertialMotion.MOVING))?.state
        )
        assertEquals(
            SignalState.NORMAL,
            filter.accept(sample(7, 28.0).copy(inertialMotion = InertialMotion.MOVING))?.state
        )
    }

    @Test
    fun `걸음이 감지되거나 속도 정확도를 모르면 저속만으로 멈추지 않는다`() {
        val freeRun = LocationFilter(isStabilizationEnabled = true)
        freeRun.accept(sample(0, 0.0))
        val slow = sample(1, 1.0).copy(speedMps = 0.1)
        assertEquals(SignalState.NORMAL, freeRun.accept(slow)?.state)
        val walking = sample(2, 2.0).copy(
            speedMps = 0.1,
            speedAccuracyMps = 0.2,
            inertialMotion = InertialMotion.MOVING
        )
        assertEquals(SignalState.NORMAL, freeRun.accept(walking)?.state)
    }
}
