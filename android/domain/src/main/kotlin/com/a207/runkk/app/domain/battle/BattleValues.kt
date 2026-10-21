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

/**
 * 대결 러닝 진행 상태. BattleRunCoordinator.state로 ui.battle에 보인다. ui.battle은 domain.run을 보지 않으므로(5.1.2)
 * 내 측정값(RunTracker 진행값)은 조정자가 [MyProgress]로 옮겨 Running·Closing에 담는다.
 */
sealed interface BattleRunState {
    data object Idle : BattleRunState

    data class Matched(val match: MatchFound) : BattleRunState

    data class Countdown(val battleId: Long, val startAt: Instant) : BattleRunState

    /**
     * 측정 중. [opponentConnection]은 랜덤 대결 상대의 연결 상태(FR-088 "상대 연결 확인 중")이고, 기록 상대(고스트·나와의 싸움·
     * 유명인)와 첫 상대 진행값 전에는 null이다.
     */
    data class Running(
        val battleId: Long,
        val gapMeters: Int?,
        val opponentDistance: Distance?,
        val lastGuidance: BattleGuidance.Kind?,
        val mine: MyProgress = MyProgress.ZERO,
        val opponentConnection: OpponentProgress.Connection? = null
    ) : BattleRunState

    /** 한 사람이 도착해 종료 카운트다운 중(FR-107). 남은 사람은 계속 달리므로 [mine]은 계속 바뀐다. */
    data class Closing(
        val battleId: Long,
        val arrivedName: String,
        val endsAt: Instant,
        val mine: MyProgress = MyProgress.ZERO
    ) : BattleRunState

    data class Finished(val battleId: Long, val status: BattleEnded.Status?) : BattleRunState
}

/** 내 측정값(RunTracker.progress에서 옮긴 값). [locked]는 화면 잠금(FR-182) 상태다. */
data class MyProgress(
    val distance: Distance,
    val elapsed: Duration,
    val currentPace: Pace?,
    val averagePace: Pace?,
    val locked: Boolean
) {
    companion object {
        val ZERO = MyProgress(Distance.ZERO, Duration.ZERO, currentPace = null, averagePace = null, locked = false)
    }
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
