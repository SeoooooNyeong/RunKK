package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 공유 카드 값(서버 running.RunCardData, D-22). 앞면은 기록, 뒷면은 지도 + 경로선(route).
 * courseMapShared가 거짓이면 뒷면 공유를 거부한다(FR-069).
 */
data class RunCard(
    val recordId: String,
    val cardKind: CardKind,
    val startedAt: Instant,
    val distance: Distance,
    val elapsed: Duration,
    val averagePace: Pace?,
    val route: List<GeoPoint>,
    val isCourseMapShared: Boolean
)
