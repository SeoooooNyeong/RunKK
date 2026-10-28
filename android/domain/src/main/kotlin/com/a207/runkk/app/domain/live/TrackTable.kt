package com.a207.runkk.app.domain.live

import java.time.Instant

/** BattleStart.opponentTrack의 형식: 경과 시간·누적 거리 표(design-review.md M39). */
data class TrackTable(val points: List<TrackPoint>)
