package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 랭킹(서버 analysis.RankingView, FR-070·FR-071). 집계 스냅숏이 아직 없으면 [aggregatedAt]이 null이다("집계 준비 중"). */
data class Ranking(
    val entries: List<Entry>,
    val mine: Entry?,
    val gapToNext: Distance?,
    val aggregatedAt: Instant?,
    val notice: String?
) {
    data class Entry(val rank: Int, val name: String, val distance: Distance)
}
