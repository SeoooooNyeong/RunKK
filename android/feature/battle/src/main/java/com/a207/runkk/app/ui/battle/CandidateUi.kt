package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import java.time.Duration
import java.time.LocalDate

/** 기록 대결 후보 한 줄(나와의 싸움 거리별 최고 기록, 유명인 공개 기록). */
data class CandidateUi(
    val celebrityId: Long?,
    val name: String,
    val distance: Distance,
    val finishTime: Duration?,
    val recordedOn: LocalDate?,
    val averagePace: Pace?,
    val sourceLabel: String?,
    val isEstimated: Boolean
) {
    /** 고를 수 있는가(기록이 없으면 "아직 기록이 없어요", FR-096). */
    val isSelectable: Boolean get() = finishTime != null

    companion object {
        fun of(c: RecordBattleCandidates.Candidate) = CandidateUi(
            celebrityId = c.id,
            name = c.displayName,
            distance = c.distance,
            finishTime = c.finishTime,
            recordedOn = c.recordedOn,
            averagePace = c.finishTime?.takeIf { c.distance.meters > 0 }?.let { Pace.of(it, c.distance) },
            sourceLabel = c.sourceLabel,
            isEstimated = c.isEstimated
        )
    }
}
