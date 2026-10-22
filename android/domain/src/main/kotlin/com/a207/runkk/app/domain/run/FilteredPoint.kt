package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.SignalState
import java.time.Instant

/** LocationFilter를 통과한 포인트와 신호 상태(FR-033). */
data class FilteredPoint(val at: Instant, val point: GeoPoint, val state: SignalState)
