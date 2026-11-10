package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.SignalState
import java.time.Duration
import java.time.Instant

/**
 * 정확도 기준으로 위치 측정값을 거른다(A-02, srs.md 6.3 2.1, FR-032·FR-033).
 * 반경 30m 초과는 버리고, 앞선 저정확도가 10초 이상이었으면 다음 포인트를 UNSTABLE로 표시한다.
 * 러닝 한 회마다 새로 만든다.
 */
class LocationFilter(private val stabilize: Boolean = false) {
    private val kalman = PositionKalmanFilter()
    private var lowAccuracySince: Instant? = null
    private var lastSeen: Instant? = null
    private var lastAccepted: LocationSample? = null
    private var heldAt: Instant? = null
    private var recoveryCandidate: LocationSample? = null

    fun accept(s: LocationSample): FilteredPoint? {
        if (stabilize) {
            if (lastSeen?.let { !s.at.isAfter(it) } == true) return null
            lastSeen = s.at
        }
        if (!s.accuracyMeters.isFinite() || s.accuracyMeters !in 0.0..MAX_ACCURACY_METERS) {
            if (lowAccuracySince == null) lowAccuracySince = s.at
            recoveryCandidate = null
            return null
        }
        val since = lowAccuracySince
        val state =
            if (since != null && Duration.between(since, s.at) >= UNSTABLE_AFTER) {
                SignalState.UNSTABLE
            } else {
                SignalState.NORMAL
            }
        lowAccuracySince = null
        return if (stabilize) acceptStable(s, state) else FilteredPoint(s.at, s.point, state)
    }

    private fun acceptStable(sample: LocationSample, signal: SignalState): FilteredPoint? {
        val previous = lastAccepted ?: return reanchor(sample, signal)
        val speed = sample.speedMps
        val speedAccuracy = sample.speedAccuracyMps
        val hold = when (sample.inertialMotion) {
            InertialMotion.STILL, InertialMotion.WARMING_UP -> true

            InertialMotion.MOVING -> false

            InertialMotion.UNKNOWN ->
                speed != null && speedAccuracy != null && speed + speedAccuracy <= STILL_SPEED_MPS
        }
        if (hold) {
            heldAt = sample.at
            recoveryCandidate = null
            return null
        }

        val baseline = heldAt?.let { previous.copy(at = it) } ?: previous
        if (signal == SignalState.NORMAL && Duration.between(baseline.at, sample.at) < UNSTABLE_AFTER) {
            if (isReachable(baseline, sample, allowPositionNoise = heldAt == null)) {
                val meters = baseline.point.metersTo(sample.point)
                // 재출발 때 속도 추정치를 새로 잡고, 실제 과속은 기존 유효성 규칙이 볼 수 있게 보존한다.
                if (heldAt != null ||
                    (confirmsOverspeed(sample) && OverspeedRule.isOverspeed(meters, baseline.at, sample.at))
                ) {
                    return reanchor(sample, SignalState.NORMAL)
                }
                kalman.update(sample)?.let { return acceptPoint(sample, SignalState.NORMAL, it) }
            }
            if (heldAt == null || sample.inertialMotion != InertialMotion.MOVING) {
                recoveryCandidate = sample
                return null
            }
        }
        return recover(sample)
    }

    private fun recover(sample: LocationSample): FilteredPoint? {
        val candidate = recoveryCandidate
        recoveryCandidate = sample
        if (candidate == null ||
            Duration.between(candidate.at, sample.at) > RECOVERY_FIX_GAP ||
            !isReachable(candidate, sample)
        ) {
            return null
        }
        return reanchor(sample, SignalState.UNSTABLE)
    }

    private fun reanchor(sample: LocationSample, state: SignalState): FilteredPoint =
        acceptPoint(sample, state, kalman.reset(sample))

    private fun acceptPoint(sample: LocationSample, state: SignalState, point: GeoPoint): FilteredPoint {
        lastAccepted = sample
        heldAt = null
        recoveryCandidate = null
        return FilteredPoint(sample.at, point, state)
    }

    private fun isReachable(from: LocationSample, to: LocationSample, allowPositionNoise: Boolean = false): Boolean {
        val meters = from.point.metersTo(to.point)
        val speed = to.speedMps
        val accuracy = to.speedAccuracyMps
        val seconds = Duration.between(from.at, to.at).toMillis() / 1_000.0
        val moving = to.inertialMotion == InertialMotion.MOVING
        val positionalNoise = if (allowPositionNoise && moving) {
            maxOf(from.accuracyMeters, to.accuracyMeters).coerceAtMost(MAX_MOVING_POSITION_NOISE_METERS)
        } else {
            0.0
        }
        val maxDistance = OverspeedRule.MAX_SEGMENT_SPEED_KMH / 3.6 * seconds
        if (!confirmsOverspeed(to) && meters > maxDistance + positionalNoise) return false
        if (speed == null || accuracy == null || (moving && speed + accuracy <= STILL_SPEED_MPS)) return true
        val plausibleMeters = maxOf(4.0, (speed + 2 * accuracy + 1.5) * seconds)
        return meters <= plausibleMeters + positionalNoise
    }

    private fun confirmsOverspeed(sample: LocationSample): Boolean {
        val speed = sample.speedMps ?: return false
        val accuracy = sample.speedAccuracyMps ?: return false
        return speed - accuracy > OverspeedRule.MAX_SEGMENT_SPEED_KMH / 3.6
    }

    companion object {
        /** 이보다 큰 정확도 반경(m)의 측정값은 버린다. GPS 예열의 준비됨 기준도 같다(W55). */
        const val MAX_ACCURACY_METERS = 30.0
        private val UNSTABLE_AFTER: Duration = Duration.ofSeconds(10)
        private val RECOVERY_FIX_GAP: Duration = Duration.ofSeconds(3)
        private const val STILL_SPEED_MPS = 0.7
        private const val MAX_MOVING_POSITION_NOISE_METERS = 4.0
    }
}
