package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 나와의 싸움·유명인 대결 후보(서버 battle.RecordBattleCandidates, FR-096·FR-097·FR-100). */
data class RecordBattleCandidates(val candidates: List<Candidate>) {
    /** id는 유명인 기록 ID(나와의 싸움은 null). finishTime이 null이면 "아직 기록이 없어요". */
    data class Candidate(
        val id: Long?,
        val displayName: String,
        val distance: Distance,
        val finishTime: Duration?,
        val recordedOn: LocalDate?,
        val sourceLabel: String?,
        val estimated: Boolean
    )
}
