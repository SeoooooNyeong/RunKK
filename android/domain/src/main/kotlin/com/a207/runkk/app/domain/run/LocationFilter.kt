package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.SignalState
import java.time.Duration
import java.time.Instant

/**
 * 정확도와 정지 여부에 따라 위치 측정값을 거른다(A-02, FR-032·FR-033, S15P31A207-280).
 * 반경 30m 초과는 버리고, 앞선 저정확도가 10초 이상이었으면 다음 포인트를 UNSTABLE로 표시한다.
 * 러닝 한 회마다 새로 만든다.
 */
class LocationFilter {
    private var lowAccuracySince: Instant? = null
    private var lastAccepted: FilteredPoint? = null

    fun accept(s: LocationSample): FilteredPoint? {
        if (s.accuracyMeters > MAX_ACCURACY_METERS) {
            if (lowAccuracySince == null) lowAccuracySince = s.at
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
        val previous = lastAccepted
        val speedUpperEstimate = s.speedUpperEstimateMps
        val point = if (state == SignalState.NORMAL &&
            speedUpperEstimate != null && speedUpperEstimate in 0.0..STATIONARY_SPEED_MPS &&
            previous != null && s.at.isAfter(previous.at) &&
            Duration.between(previous.at, s.at) <= MAX_STATIONARY_SAMPLE_GAP
        ) {
            // 좌표를 고정해도 이 표본의 시각은 거리·페이스와 서버의 구간 계산에 필요하다.
            previous.point
        } else {
            s.point
        }
        return FilteredPoint(s.at, point, state).also { lastAccepted = it }
    }

    fun resetStationaryAnchor() {
        lastAccepted = null
    }

    companion object {
        /** 이보다 큰 정확도 반경(m)의 측정값은 버린다. GPS 예열의 준비됨 기준도 같다(W55). */
        const val MAX_ACCURACY_METERS = 30.0
        private val UNSTABLE_AFTER: Duration = Duration.ofSeconds(10)
        private const val STATIONARY_SPEED_MPS = 0.5
        private val MAX_STATIONARY_SAMPLE_GAP: Duration = Duration.ofSeconds(3)
    }
}
