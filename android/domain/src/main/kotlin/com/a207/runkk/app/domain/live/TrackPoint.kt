package com.a207.runkk.app.domain.live

import java.time.Instant

/** 표의 한 줄(경과 초, 누적 미터). */
data class TrackPoint(val elapsedSeconds: Long, val distanceMeters: Long)
