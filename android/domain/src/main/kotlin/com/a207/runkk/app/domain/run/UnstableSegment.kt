package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.SignalState
import java.time.Instant

/** 불안정 구간: 시작·끝 일시와 건너뛴 직선 거리(누적 거리에서 뺀다, FR-033, Q-18). */
data class UnstableSegment(val startAt: Instant, val endAt: Instant, val distance: Distance)
