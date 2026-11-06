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
    /** 서버 BattleType. 이력 응답은 고스트를 RANDOM으로 묶어 보내고, 배틀런 필터(RANDOM)는 FRIEND도 함께 받는다(D-281, 서버 D-230). */
    enum class Type {
        RANDOM,
        GHOST,
        FRIEND,
        SELF,
        CELEBRITY
    }

    /**
     * [gap]은 기록 차이(본인 − 상대, 음수면 본인이 빨랐다, 없으면 null), [sourceLabel]은 유명인 기록 출처 표기,
     * [isNewRecord]는 나와의 싸움 신기록이다. [placementRound]는 배치고사 판 번호(만든 때, 아니면 null, J24-22),
     * [opponentMemberId]는 친구 대결 상대 회원 ID(상대가 탈퇴했거나 친구 대결이 아니면 null, D-492)다.
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
        val isNewRecord: Boolean = false,
        val placementRound: Int? = null,
        val opponentMemberId: Long? = null
    )
}
