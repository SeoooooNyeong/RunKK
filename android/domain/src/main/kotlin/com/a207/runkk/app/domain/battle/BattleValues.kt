package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.MatchFound
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 나와의 싸움·유명인 대결 시작 정보(POST /battles/self·celebrity 응답, design-review.md M34).
 * cardKind는 SELF_RUN 또는 CELEB_RUN이다.
 */
data class RecordBattleStart(
    val battleId: Long,
    val opponentName: String,
    val distance: Distance,
    val track: OpponentTrack,
    val cardKind: CardKind
)

/** 대결 러닝 진행 상태. BattleRunCoordinator.state로 ui.battle에 보인다. */
sealed interface BattleRunState {
    data object Idle : BattleRunState

    data class Matched(val match: MatchFound) : BattleRunState

    data class Countdown(val battleId: Long, val startAt: Instant) : BattleRunState

    data class Running(
        val battleId: Long,
        val gapMeters: Int?,
        val opponentDistance: Distance?,
        val lastGuidance: BattleGuidance.Kind?
    ) : BattleRunState

    data class Closing(val battleId: Long, val arrivedName: String, val endsAt: Instant) : BattleRunState

    data class Finished(val battleId: Long, val status: BattleEnded.Status?) : BattleRunState
}

/** 매칭 요청·취소 결과(서버 battle.MatchAcceptance). 거부는 AppError.Rejected(사유 코드)로 온다. */
data class MatchAcceptance(val waiting: Boolean, val distance: Distance?)

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

/** 대결 결과(서버 battle.BattleResultView + 적립 리워드, FR-093·FR-108). */
data class BattleResult(
    val battleId: Long,
    val pending: Boolean,
    val verdict: String?,
    val rows: List<Row>,
    val rewardPoints: Int?,
    val newRecord: Boolean
) {
    data class Row(val item: String, val mine: String, val theirs: String)
}

/** 대결 이력(서버 battle.BattleHistoryView, FR-101). */
data class BattleHistory(
    val entries: List<Entry>,
    val total: Int,
    val wins: Int,
    val losses: Int,
    val selfNewRecords: Int
) {
    enum class Type {
        RANDOM,
        GHOST,
        SELF,
        CELEBRITY
    }

    data class Entry(
        val battleId: Long,
        val type: Type,
        val opponentName: String,
        val distance: Distance,
        val verdict: String?,
        val playedAt: Instant
    )
}
