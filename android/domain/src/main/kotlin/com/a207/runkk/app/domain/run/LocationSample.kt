package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.GeoPoint
import java.time.Instant

/** 속도 상단 추정치(m/s)는 기기가 측정한 속도에 속도 오차를 더한 값이다. 둘 중 하나가 없으면 null이다. */
data class LocationSample(
    val at: Instant,
    val point: GeoPoint,
    val accuracyMeters: Double,
    val speedUpperEstimateMps: Double? = null
)
