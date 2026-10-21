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

/**
 * 대결 결과(서버 battle.BattleResultView + 적립 리워드, FR-093·FR-108). [opponentName]은 상대 표시명, [recordId]는 이 대결의
 * 본인 러닝 기록 ID(기록 카드, 아직 없으면 null), [myReach]·[opponentReach]는 도달 시간, [weekly]는 이번 주 전적이다.
 */
data class BattleResult(
    val battleId: Long,
    val pending: Boolean,
    val verdict: String?,
    val rows: List<Row>,
    val rewardPoints: Int?,
    val newRecord: Boolean,
    val opponentName: String? = null,
    val recordId: String? = null,
    val myReach: Duration? = null,
    val opponentReach: Duration? = null,
    val weekly: Weekly? = null
) {
    data class Row(val item: String, val mine: String, val theirs: String)

    /** 이번 주 전적(서버 battle.WeeklyRecord). */
    data class Weekly(val wins: Int, val losses: Int, val draws: Int)
}
