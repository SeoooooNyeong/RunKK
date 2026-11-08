package com.a207.runkk.watch.heart

import androidx.health.services.client.data.DataType
import kotlin.test.Test
import kotlin.test.assertEquals

/** stepDataTypesOf(S15P31A207-242): 동행 운동 세션에 넣을 손목 걸음 종류. */
class StepDataTypesTest {
    @Test
    fun `분당 걸음 수를 먼저, 없으면 누적 걸음 수를, 둘 다 없으면 넣지 않는다`() {
        assertEquals(
            setOf(DataType.STEPS_PER_MINUTE),
            stepDataTypesOf(setOf(DataType.HEART_RATE_BPM, DataType.STEPS, DataType.STEPS_PER_MINUTE))
        )
        assertEquals(setOf(DataType.STEPS), stepDataTypesOf(setOf(DataType.HEART_RATE_BPM, DataType.STEPS)))
        assertEquals(emptySet(), stepDataTypesOf(setOf(DataType.HEART_RATE_BPM)))
    }
}
