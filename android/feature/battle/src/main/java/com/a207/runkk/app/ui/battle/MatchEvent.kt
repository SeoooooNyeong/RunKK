package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.navigation.Routes

sealed interface MatchEvent {
    /** 측정 시작: 대결 진행 화면으로(이 화면을 대신한다). */
    data class Started(val key: Routes.BattleRunning) : MatchEvent

    /** 대기 취소: 이전 화면으로. */
    data object Exit : MatchEvent
}
