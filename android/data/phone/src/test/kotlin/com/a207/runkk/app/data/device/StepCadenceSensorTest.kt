package com.a207.runkk.app.data.device

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** StepCadenceSensor(S15P31A207-242): 걸음 센서 시각(부팅 뒤 나노초)을 벽시계로 바꾼다. */
class StepCadenceSensorTest {
    @Test
    fun `센서 시각은 지금보다 앞선 만큼 벽시계에서 뺀다`() {
        val now = Instant.parse("2026-11-08T07:00:10Z")

        val at = StepCadenceSensor.eventInstant(
            eventNanos = 4_000_000_000L,
            nowElapsedNanos = 5_500_000_000L,
            nowEpochMillis = now.toEpochMilli()
        )

        assertEquals(Instant.parse("2026-11-08T07:00:08.500Z"), at)
    }
}
