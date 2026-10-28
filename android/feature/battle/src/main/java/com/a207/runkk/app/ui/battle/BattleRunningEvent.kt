package com.a207.runkk.app.ui.battle

sealed interface BattleRunningEvent {
    data class Finished(val battleId: Long) : BattleRunningEvent
}
