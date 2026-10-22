package com.a207.runkk.watch.heart

import kotlin.test.Test
import kotlin.test.assertEquals

/** Health Services 센서 값 → WatchHeartRate: 신뢰할 수 없는 값은 실패 측정으로 표시한다(FR-041). */
class ClassifyHeartRateTest {
    @Test
    fun `신뢰할 수 있는 양수 값은 반올림한 정상 측정`() {
        assertEquals(WatchHeartRate(1_000, 142, WatchHeartRate.Status.OK), classifyHeartRate(1_000, 141.6, true))
    }

    @Test
    fun `접촉 없음 신뢰 불가는 실패 측정`() {
        assertEquals(WatchHeartRate(1_000, 0, WatchHeartRate.Status.FAILED), classifyHeartRate(1_000, 141.0, false))
    }

    @Test
    fun `0 이하나 숫자가 아닌 값은 실패 측정`() {
        assertEquals(WatchHeartRate.Status.FAILED, classifyHeartRate(1_000, 0.0, true).status)
        assertEquals(WatchHeartRate.Status.FAILED, classifyHeartRate(1_000, -3.0, true).status)
        assertEquals(WatchHeartRate.Status.FAILED, classifyHeartRate(1_000, Double.NaN, true).status)
    }
}
