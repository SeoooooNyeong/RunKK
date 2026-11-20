package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GpsReadiness
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

    /** 랜덤 대결 성사. [gps]는 출발 전 GPS 예열 상태(W55)이고 예열하지 않으면(위치 권한 없음 등) null이다. */
    data class Matched(val match: MatchFound, val gps: GpsReadiness? = null) : BattleRunState

    /**
     * 출발 카운트다운. [gps]는 [Matched]와 같고, 기록 대결(나와의 싸움·유명인)은 예열하지 않아 null이다.
     * [isAwaitingServer]면 셀프런·셀럽런 대결 만들기 응답을 아직 받지 못했다(S15P31A207-335 9회차). [battleId]는 그동안 임시 번호(음수)이고,
     * 화면은 응답이 올 때까지 숫자 3에서 멈춘다(응답을 받으면 출발 = max(누름 + 3초, 응답 + 2초)).
     */
    data class Countdown(
        val battleId: Long,
        val startAt: Instant,
        val gps: GpsReadiness? = null,
        val isAwaitingServer: Boolean = false
    ) : BattleRunState

    /**
     * 셀프런·셀럽런 대결 만들기가 실패했거나 제한 시간 안에 응답이 없어 카운트다운을 멈췄다(S15P31A207-335 9회차). [battleId]는 임시 번호다.
     * 회는 열려 있어 BattleRunCoordinator.retryRecordBattle로 다시 보내거나 cancel로 닫는다(서버에 만든 대결이 없어 보낼 것이 없다).
     */
    data class StartFailed(val battleId: Long, val error: AppError) : BattleRunState

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

    /**
     * 셀프런·셀럽런을 그만둬 기록을 버리고 끝났다(D-266). 서버는 대결을 취소해 결과·이력·리워드가 없으므로 화면은 결과 화면 없이
     * 기록런 탭으로 돌아간다.
     */
    data class Discarded(val battleId: Long) : BattleRunState
}
