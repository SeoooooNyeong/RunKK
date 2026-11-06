package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.SignalState
import java.time.Duration
import java.time.Instant

/**
 * 정확도 기준으로 위치 측정값을 거른다(A-02, srs.md 6.3 2.1, FR-032·FR-033).
 * 반경 30m 초과는 버리고, 앞선 저정확도가 10초 이상이었으면 다음 포인트를 UNSTABLE로 표시한다.
 * 러닝 한 회마다 새로 만든다.
 */
class LocationFilter {
    private var lowAccuracySince: Instant? = null

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
        return FilteredPoint(s.at, s.point, state)
    }

    companion object {
        /** 이보다 큰 정확도 반경(m)의 측정값은 버린다. GPS 예열의 준비됨 기준도 같다(W55). */
        const val MAX_ACCURACY_METERS = 30.0
        private val UNSTABLE_AFTER: Duration = Duration.ofSeconds(10)
    }
}
