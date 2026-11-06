package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.domain.battle.BattleResult

/** 대결 결과 화면의 한 번 처리 이동. */
sealed interface BattleResultEvent {
    /** 이 대결 기록이 티어 평가를 일으켰다(D-284): 평가 화면을 한 번 연다. */
    data class NavigateEvaluation(val evaluation: BattleResult.Evaluation) : BattleResultEvent

    /** 막 끝난 배치고사 판이다(D-501): 이 화면을 배치고사 판 결과(Routes.PlacementResult)로 바꿔 연다. */
    data class OpenPlacementResult(val battleId: Long) : BattleResultEvent
}
