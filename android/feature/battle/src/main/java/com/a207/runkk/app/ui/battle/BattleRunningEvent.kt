package com.a207.runkk.app.ui.battle

sealed interface BattleRunningEvent {
    data class Finished(val battleId: Long) : BattleRunningEvent

    /** 셀프런·셀럽런을 그만둬 기록을 버렸다(D-266): 결과 화면 없이 기록런 탭으로. */
    data object Discarded : BattleRunningEvent
}
