package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.SignalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `정지 표본은 직전 좌표와 새 시각을 쓰고 걸음이 재개되면 새 좌표를 쓴다`() {
        val start = sample(0, 0.0)
        filter.accept(start)
        for (second in 1..60L) {
            val drift = sample(second, if (second % 2L == 0L) -4.0 else 4.0, accuracy = 25.0)
                .copy(speedUpperEstimateMps = 0.5)
            val accepted = filter.accept(drift)
            assertEquals(start.point, accepted?.point)
            assertEquals(drift.at, accepted?.at)
        }
        val walking = sample(61, 2.0).copy(speedUpperEstimateMps = 0.8)
        assertEquals(walking.point, filter.accept(walking)?.point)
    }

    @Test
    fun `속도 정보가 없거나 마지막 표본이 오래됐으면 받은 좌표를 쓴다`() {
        filter.accept(sample(0, 0.0))
        val unknown = sample(1, 4.0)
        assertEquals(unknown.point, filter.accept(unknown)?.point)
        val afterGap = sample(5, 8.0).copy(speedUpperEstimateMps = 0.2)
        assertEquals(afterGap.point, filter.accept(afterGap)?.point)
    }

    @Test
    fun `저정확도가 이어진 뒤에는 정지 표본도 회복점 좌표로 새 기준을 잡는다`() {
        filter.accept(sample(0, 0.0))
        assertNull(filter.accept(sample(1, 40.0, accuracy = 50.0).copy(speedUpperEstimateMps = 0.1)))
        val recovery = sample(11, 18.0).copy(speedUpperEstimateMps = 0.1)
        val accepted = filter.accept(recovery)
        assertEquals(SignalState.UNSTABLE, accepted?.state)
        assertEquals(recovery.point, accepted?.point)
        assertEquals(recovery.point, filter.accept(sample(12, 25.0).copy(speedUpperEstimateMps = 0.1))?.point)
    }
}
