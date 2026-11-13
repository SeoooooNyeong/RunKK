package com.a207.runkk.app.domain.run

import com.a207.runkk.app.domain.testing.T0
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CadenceMeter(S15P31A207-242): 누적 걸음 수로 최근 10초 케이던스를 셈한다. */
class CadenceMeterTest {
    private fun at(millis: Long): Instant = T0.plusMillis(millis)

    @Test
    fun `5초가 지나기 전에는 값을 내지 않고 그 뒤 분당 걸음 수를 낸다`() {
        val meter = CadenceMeter()
        assertNull(meter.accept(at(0), 1_000))
        assertNull(meter.accept(at(4_000), 1_012))
        assertEquals(180.0, meter.accept(at(5_000), 1_015))
    }

    @Test
    fun `창은 최근 10초이고 값은 1초에 한 번만 낸다`() {
        val meter = CadenceMeter()
        // 처음 20초는 분당 120걸음, 그 뒤는 분당 180걸음
        var steps = 0L
        var last: Double? = null
        for (second in 1..20L) {
            steps += 2
            meter.accept(at(second * 1_000), steps)
        }
        for (second in 21..40L) {
            steps += 3
            meter.accept(at(second * 1_000), steps)?.let { last = it }
        }
        assertEquals(180.0, last)
        assertNull(meter.accept(at(40_500), steps + 1), "1초 안의 두 번째 값은 내지 않는다")
    }

    @Test
    fun `누적 값이 줄면 처음부터 다시 센다`() {
        val meter = CadenceMeter()
        meter.accept(at(0), 5_000)
        meter.accept(at(6_000), 5_018)
        assertNull(meter.accept(at(7_000), 3), "센서가 다시 시작했다")
        assertEquals(120.0, meter.accept(at(12_000), 13))
    }

    @Test
    fun `움직임 표본은 값이 하나라도 있고 음수·무한대가 아닐 때만 쓴다`() {
        assertTrue(MotionSample(T0, speedMps = 3.0).isUsable)
        assertTrue(MotionSample(T0, cadenceSpm = 0.0).isUsable)
        assertFalse(MotionSample(T0).isUsable)
        assertFalse(MotionSample(T0, speedMps = -1.0).isUsable)
        assertFalse(MotionSample(T0, speedMps = 3.0, cadenceSpm = Double.NaN).isUsable)
    }
}
