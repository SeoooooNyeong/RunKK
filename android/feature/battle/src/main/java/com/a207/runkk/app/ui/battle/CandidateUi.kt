package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunnerLook
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
    val isEstimated: Boolean,
    /** 셀럽 후보의 그 거리 내 최고 기록(셀럽런 카운트다운, 없으면 "첫 도전"). */
    val myBest: Duration? = null,
    /** 셀럽 그림 키(서버 celebrityFigure, 없으면 공통 그림). */
    val celebrityFigure: String? = null,
    /** 그 기록에 저장된 내 모습(그날의 나, [임시:D-성-272]). 셀럽 후보·기록 없는 거리면 null이다. */
    val look: RunnerLook? = null
) {
    /** 고를 수 있는가(기록이 없으면 "기록 없음"이고 누를 수 없다, FR-096, D-288). */
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
            isEstimated = c.isEstimated,
            myBest = c.myBest,
            celebrityFigure = c.celebrityFigure,
            look = c.look
        )
    }
}
