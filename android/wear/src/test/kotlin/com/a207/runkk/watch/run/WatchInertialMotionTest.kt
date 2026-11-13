package com.a207.runkk.watch.run

import com.a207.runkk.app.domain.run.InertialMotion
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchInertialMotionTest {
    @Test
    fun `손목 회전은 정지로 남기고 보행과 다시 멈추는 시각을 저장한다`() {
        val motion = WatchInertialMotion(null)
        for (at in 0L..1_500L step 50) motion.onAcceleration(at, 0.1)
        assertEquals(InertialMotion.STILL, motion.stateAt(1_500, 1_500))

        for (at in 1_550L..3_500L step 50) {
            motion.onRotation(at, 3.0)
            val acceleration = if (at % 500L < 150L) 2.6 else 0.25
            motion.onAcceleration(at, acceleration)
        }
        assertEquals(InertialMotion.STILL, motion.stateAt(3_500, 3_500))

        for (at in 3_550L..4_700L step 50) {
            motion.onRotation(at, 0.8)
            motion.onAcceleration(at, 1.2)
        }
        assertEquals(InertialMotion.MOVING, motion.stateAt(4_700, 4_700))

        for (at in 4_750L..6_500L step 50) motion.onAcceleration(at, 0.1)
        assertEquals(InertialMotion.STILL, motion.stateAt(6_500, 6_500))
        assertEquals(InertialMotion.MOVING, motion.stateAt(4_700, 100_000))
        assertEquals(InertialMotion.UNKNOWN, motion.stateAt(6_500, 400_000))
        assertEquals(InertialMotion.UNKNOWN, motion.stateAt(7_500, 7_500))
        motion.stop()
        assertEquals(InertialMotion.UNKNOWN, motion.stateAt(4_700, 100_000))
    }
}
