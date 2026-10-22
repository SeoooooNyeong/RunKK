package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.SignalState
import java.time.Instant

/** 기록에 담는 경로 포인트(CompletedRun.route). */
data class RoutePoint(val at: Instant, val point: GeoPoint, val state: SignalState)
