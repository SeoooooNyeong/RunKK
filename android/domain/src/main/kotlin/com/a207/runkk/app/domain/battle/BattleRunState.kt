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
