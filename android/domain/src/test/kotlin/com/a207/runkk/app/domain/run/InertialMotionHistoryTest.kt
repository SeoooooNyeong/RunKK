package com.a207.runkk.app.domain.run

import kotlin.test.Test
import kotlin.test.assertEquals

class InertialMotionHistoryTest {
    @Test
    fun `늦게 온 위치에는 측정 당시 상태를 적용한다`() {
        val history = InertialMotionHistory()
        history.record(1_000, InertialMotion.STILL)
        history.record(1_400, InertialMotion.MOVING)
        history.record(5_000, InertialMotion.STILL)

        assertEquals(InertialMotion.STILL, history.stateAt(1_250, 5_000))
        assertEquals(InertialMotion.MOVING, history.stateAt(1_600, 5_000))
        assertEquals(InertialMotion.UNKNOWN, history.stateAt(3_000, 5_000))
        assertEquals(InertialMotion.UNKNOWN, history.stateAt(900, 5_000))
        assertEquals(InertialMotion.UNKNOWN, history.stateAt(5_100, 5_000))
        assertEquals(InertialMotion.UNKNOWN, history.stateAt(1_600, 400_000))
        history.clear()
        assertEquals(InertialMotion.UNKNOWN, history.stateAt(5_000, 5_000))
    }
}
