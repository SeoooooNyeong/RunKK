package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 나와의 싸움·유명인 대결 후보(서버 battle.RecordBattleCandidates, FR-096·FR-097·FR-100). */
data class RecordBattleCandidates(val candidates: List<Candidate>) {
    /**
     * id는 유명인 기록 ID(나와의 싸움은 null). finishTime이 null이면 "기록 없음". [myBest]는 유명인 후보의 그 거리 내 최고 기록
     * (셀럽 카운트다운 "10km 최고 1:02:10", 없으면 "첫 도전", 서버 D-238)이고 나와의 싸움은 null이다. [celebrityFigure]는 셀럽 그림 키
     * (서버 celebrityFigure, 없으면 공통 그림)다.
     */
    data class Candidate(
        val id: Long?,
        val displayName: String,
        val distance: Distance,
        val finishTime: Duration?,
        val recordedOn: LocalDate?,
        val sourceLabel: String?,
        val isEstimated: Boolean,
        val myBest: Duration? = null,
        val celebrityFigure: String? = null,
        /** 그 기록에 저장된 모습(그날의 나, [임시:D-성-272]). 유명인 후보·기록 없는 거리면 null이다. */
        val look: RunnerLook? = null
    )
}
