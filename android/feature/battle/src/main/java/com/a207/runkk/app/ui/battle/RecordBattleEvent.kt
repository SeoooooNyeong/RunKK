package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.navigation.Routes

/** 기록 대결 화면의 한 번 처리 이동. */
sealed interface RecordBattleEvent {
    data class Start(val key: Routes.BattleRunning) : RecordBattleEvent
}
