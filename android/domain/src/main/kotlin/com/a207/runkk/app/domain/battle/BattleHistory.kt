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

/** 대결 이력(서버 battle.BattleHistoryView, FR-101). [nextCursor]가 null이면 마지막 쪽이다. */
data class BattleHistory(
    val entries: List<Entry>,
    val total: Int,
    val wins: Int,
    val losses: Int,
    val selfNewRecords: Int,
    val nextCursor: Long? = null
) {
    enum class Type {
        RANDOM,
        GHOST,
        SELF,
        CELEBRITY
    }

    /**
     * [gap]은 기록 차이(본인 − 상대, 음수면 본인이 빨랐다, 없으면 null), [sourceLabel]은 유명인 기록 출처 표기,
     * [newRecord]는 나와의 싸움 신기록이다.
     */
    data class Entry(
        val battleId: Long,
        val type: Type,
        val opponentName: String,
        val distance: Distance,
        val verdict: String?,
        val playedAt: Instant,
        val gap: Duration? = null,
        val sourceLabel: String? = null,
        val newRecord: Boolean = false
    )
}
