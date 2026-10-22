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
}
