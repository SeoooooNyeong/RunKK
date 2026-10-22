package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.SignalState
import java.time.Instant

/** 검증된 심박 표본(측정일시, bpm). 실패 측정은 data.watch가 걸러서 넘긴다(FR-043, D-54). */
data class HeartRateSample(val at: Instant, val bpm: Int)
