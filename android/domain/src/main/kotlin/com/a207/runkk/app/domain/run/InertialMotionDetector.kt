package com.a207.runkk.app.domain.run

/** 폰의 가속도와 회전으로 정지·이동을 구분한다. 시각은 부팅 뒤 경과 밀리초다. */
class InertialMotionDetector(private val isGaitTrackingDuringRotationEnabled: Boolean = true) {
    private var state = InertialMotion.UNKNOWN
    private var startedAt = 0L
    private var lastAt: Long? = null
    private var quietSince: Long? = null
    private var motionSince: Long? = null
    private var motionSamples = 0
    private var lastRotationAt: Long? = null
    private var lastStepAt: Long? = null
    private var lastConfirmedStepAt: Long? = null
    private var lastGaitPeakAt: Long? = null
    private var gaitPeaks = 0
    private var gaitPeakArmed = true

    fun onStep(at: Long) {
        val previous = lastStepAt
        if (previous != null && at <= previous) return
        lastStepAt = at
        if (previous != null && at - previous in MIN_GAIT_INTERVAL_MS..STEP_CONTINUITY_MS) {
            lastConfirmedStepAt = at
            state = InertialMotion.MOVING
            quietSince = null
        }
    }

    fun onRotation(at: Long, angularSpeedRps: Double) {
        if (!angularSpeedRps.isFinite() || angularSpeedRps < HANDLING_ROTATION) return
        if (lastRotationAt?.let { at <= it } == true) return
        lastRotationAt = at
        motionSince = null
        quietSince = null
    }

    fun onSample(at: Long, linearAccelerationMps2: Double) {
        val previous = lastAt
        if (previous != null && at <= previous) return
        if (!linearAccelerationMps2.isFinite() || linearAccelerationMps2 < 0) return
        if (previous == null || at - previous > SAMPLE_GAP_MS) {
            startedAt = at
            state = InertialMotion.WARMING_UP
            quietSince = null
            motionSince = null
            lastGaitPeakAt = null
            gaitPeakArmed = true
        }
        lastAt = at

        // 회전 중 단발 가속은 버리되 일정 간격으로 반복되는 보행 충격은 출발 근거로 쓴다.
        if (isHandling(at)) {
            if (isGaitTrackingDuringRotationEnabled) trackGait(at, linearAccelerationMps2)
            return
        }
        lastGaitPeakAt = null
        gaitPeakArmed = true

        val quiet = linearAccelerationMps2 <= QUIET_ACCEL
        quietSince = if (quiet) quietSince ?: at else null
        val recentSteps = lastConfirmedStepAt?.let { at - it in 0..STEP_CONTINUITY_MS } == true
        if (!recentSteps && quietSince?.let { at - it >= STILL_AFTER_MS } == true) {
            state = InertialMotion.STILL
            motionSince = null
            return
        }

        if (linearAccelerationMps2 >= MOVE_ACCEL) {
            val first = motionSince
            if (first == null || at - first > MOTION_WINDOW_MS) {
                motionSince = at
                motionSamples = 1
            } else {
                motionSamples++
                if (motionSamples >= 3 && at - first >= MOTION_MIN_SPAN_MS) state = InertialMotion.MOVING
            }
        }
    }

    private fun trackGait(at: Long, acceleration: Double) {
        if (acceleration <= GAIT_VALLEY) gaitPeakArmed = true
        if (!gaitPeakArmed || acceleration < MOVE_ACCEL) return
        gaitPeakArmed = false
        val previous = lastGaitPeakAt
        if (previous != null && at - previous < MIN_GAIT_INTERVAL_MS) return
        gaitPeaks = if (previous != null && at - previous <= MAX_GAIT_INTERVAL_MS) gaitPeaks + 1 else 1
        lastGaitPeakAt = at
        if (gaitPeaks >= 3) state = InertialMotion.MOVING
    }

    fun stateAt(at: Long): InertialMotion {
        val last = lastAt ?: return InertialMotion.UNKNOWN
        if (at - last !in 0..SAMPLE_GAP_MS) return InertialMotion.UNKNOWN
        if (state == InertialMotion.WARMING_UP && isHandling(at)) return InertialMotion.WARMING_UP
        if (state == InertialMotion.WARMING_UP && at - startedAt > WARMUP_MS) return InertialMotion.UNKNOWN
        return state
    }

    private fun isHandling(at: Long): Boolean = lastRotationAt?.let { at - it <= ROTATION_SETTLE_MS } == true

    private companion object {
        // shortcut: 초기 임계값이며, 손·주머니 등 기기 휴대 방식별 실측으로 조정한다.
        const val QUIET_ACCEL = 0.35
        const val HANDLING_ROTATION = 0.4
        const val ROTATION_SETTLE_MS = 500L
        const val MOVE_ACCEL = 0.8
        const val GAIT_VALLEY = 0.45
        const val MIN_GAIT_INTERVAL_MS = 230L
        const val MAX_GAIT_INTERVAL_MS = 1_400L
        const val STEP_CONTINUITY_MS = 2_000L
        const val STILL_AFTER_MS = 1_500L
        const val MOTION_WINDOW_MS = 450L
        const val MOTION_MIN_SPAN_MS = 150L
        const val SAMPLE_GAP_MS = 600L
        const val WARMUP_MS = 2_000L
    }
}
