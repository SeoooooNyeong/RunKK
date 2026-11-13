package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.GeoPoint
import java.time.Instant

/** 위치 측정값(측정일시, 좌표, 정확도 반경 m). data.device.MeasurementService가 넘긴다(FR-032). */
data class LocationSample(
    val at: Instant,
    val point: GeoPoint,
    val accuracyMeters: Double,
    val speedMps: Double? = null,
    val speedAccuracyMps: Double? = null,
    val inertialMotion: InertialMotion = InertialMotion.UNKNOWN
)
