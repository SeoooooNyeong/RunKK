package com.a207.runkk.watch.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import java.time.Instant

/**
 * 워치 화면이 보는 대결 한 회의 단계. BattleRunCoordinator.state(BattleRunState)를 watch.ui가 쓰는 값만 남겨 옮긴 것이다(R75 결정,
 * W30): watch.ui는 domain.battle 형식을 보지 않고 WatchBattleFlow.steps만 구독한다.
 */
sealed interface BattleStep {
    /** 출발했거나 출발을 세는 중이라 끝나지 않은 회인가(측정 서비스가 밖에서 끝내면 이 회도 닫는다). */
    val isOpen: Boolean get() = this is Countdown || this is Running || this is Closing

    data object Idle : BattleStep

    /** 랜덤 매칭 성사(FR-087). [placementRound]는 배치고사 대결이면 판 번호(1~3, MatchFound.placementRound), 아니면 null이다(FR-276). */
    data class Matched(
        val battleId: Long,
        val distance: Distance,
        val opponentName: String,
        val opponentGender: Gender?,
        val placementRound: Int? = null
    ) : BattleStep

    /** 출발 카운트다운. 오프라인 기록 대결의 [battleId]는 0이다. */
    data class Countdown(val battleId: Long, val startAt: Instant) : BattleStep

    /** 측정 중. [gapMeters]는 상대와의 거리 차(앞서면 양수), [lead]는 마지막 추월 안내다(화면은 바뀌었는지만 견준다). */
    data class Running(val gapMeters: Int?, val lead: Any?) : BattleStep

    /** 한 사람이 도착해 종료 카운트다운 중(FR-107). */
    data object Closing : BattleStep

    data class Finished(val battleId: Long) : BattleStep

    /** 셀프·셀럽런을 그만둬 결과 없이 끝났다(D-266). */
    data object Discarded : BattleStep
}
