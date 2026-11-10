package com.a207.runkk.app.domain.run

import kotlin.test.Test
import kotlin.test.assertEquals

class InertialMotionDetectorTest {
    @Test
    fun `천천히 걸을 때 폰이 조용해도 확인된 걸음 사이에는 이동을 유지한다`() {
        val detector = InertialMotionDetector()
        for (at in 0L..1_500L step 50) detector.onSample(at, 0.1)
        detector.onStep(1_600)
        for (at in 1_550L..3_200L step 50) detector.onSample(at, 0.1)
        detector.onStep(3_200)
        assertEquals(InertialMotion.MOVING, detector.stateAt(3_200))
        for (at in 3_250L..4_800L step 50) detector.onSample(at, 0.1)
        assertEquals(InertialMotion.MOVING, detector.stateAt(4_800))
        for (at in 4_850L..6_700L step 50) detector.onSample(at, 0.1)
        assertEquals(InertialMotion.STILL, detector.stateAt(6_700))
    }

    private fun InertialMotionDetector.onSample(at: Long, acceleration: Double, rotation: Double) {
        onRotation(at, rotation)
        onSample(at, acceleration)
    }

    @Test
    fun `손이 계속 회전해도 걷는 가속 패턴이나 두 걸음이 들어오면 풀린다`() {
        val detector = InertialMotionDetector()
        for (at in 0L..1_500L step 50) detector.onSample(at, 0.1, 0.02)
        for (at in 1_550L..3_100L step 50) {
            val acceleration = if ((at - 1_550L) % 500L < 100L) 1.4 else 0.25
            detector.onSample(at, acceleration, 1.1)
        }
        assertEquals(InertialMotion.MOVING, detector.stateAt(3_100))

        val withSteps = InertialMotionDetector()
        for (at in 0L..1_500L step 50) withSteps.onSample(at, 0.1, 0.02)
        withSteps.onSample(1_600, 0.1, 1.1)
        withSteps.onStep(1_600)
        assertEquals(InertialMotion.STILL, withSteps.stateAt(1_600))
        withSteps.onSample(2_100, 0.1, 1.1)
        withSteps.onStep(2_100)
        assertEquals(InertialMotion.MOVING, withSteps.stateAt(2_100))
    }

    @Test
    fun `정지와 걷기 전환을 감지하며 폰만 돌리는 동작은 정지를 풀지 않는다`() {
        val detector = InertialMotionDetector()
        for (at in 0L..1_500L step 50) detector.onSample(at, 0.08, 0.02)
        assertEquals(InertialMotion.STILL, detector.stateAt(1_500))

        for (at in 1_550L..1_850L step 50) detector.onSample(at, 2.8, 0.65)
        assertEquals(InertialMotion.STILL, detector.stateAt(1_850), "천천히 돌려도 정지")

        for (at in 1_900L..2_200L step 50) detector.onSample(at, 4.2, 3.0)
        assertEquals(InertialMotion.STILL, detector.stateAt(2_200), "빠르게 돌려도 정지")

        for (at in 2_250L..2_650L step 50) detector.onSample(at, 1.6, 0.02)
        assertEquals(InertialMotion.STILL, detector.stateAt(2_650), "회전 직후의 잔류 가속도 무시")

        for (at in 2_700L..3_100L step 50) detector.onSample(at, 1.2, 0.25)
        assertEquals(InertialMotion.MOVING, detector.stateAt(3_100))

        for (at in 3_150L..3_450L step 50) detector.onSample(at, 1.5, 2.0)
        assertEquals(InertialMotion.MOVING, detector.stateAt(3_450), "달리던 중에는 폰을 돌려도 계속 이동")

        for (at in 3_500L..5_500L step 50) detector.onSample(at, 0.10, 0.02)
        assertEquals(InertialMotion.STILL, detector.stateAt(5_500))
    }

    @Test
    fun `충격 하나로 움직임으로 바뀌지 않고 센서가 끊기면 상태를 버린다`() {
        val detector = InertialMotionDetector()
        assertEquals(InertialMotion.UNKNOWN, detector.stateAt(0))
        detector.onSample(0, 0.08)
        assertEquals(InertialMotion.WARMING_UP, detector.stateAt(0))
        for (at in 50L..1_500L step 50) detector.onSample(at, 0.08)
        detector.onSample(1_550, 6.0)
        detector.onSample(1_600, 0.08)
        assertEquals(InertialMotion.STILL, detector.stateAt(1_600))
        assertEquals(InertialMotion.UNKNOWN, detector.stateAt(2_500))
        detector.onSample(2_550, 0.08)
        assertEquals(InertialMotion.WARMING_UP, detector.stateAt(2_550))
    }
}
