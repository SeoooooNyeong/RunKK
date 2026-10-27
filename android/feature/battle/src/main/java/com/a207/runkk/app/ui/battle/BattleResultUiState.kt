package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.battle.BattleResult

data class BattleResultUiState(
    val isLoading: Boolean = true,
    val battleId: Long? = null,
    val result: BattleResult? = null,
    val error: UiText? = null,
    /** 보류 결과를 다시 읽는 중인가. 이 동안은 결과를 확인하는 화면을, 다시 읽기를 멈춘 뒤에는 늦어짐 안내를 보인다. */
    val isAwaitingVerdict: Boolean = false
) {
    val verdict: Verdict? get() = Verdict.of(result?.verdict)

    /** 이번 주 전적(결과 응답 weekly). */
    val weekly: BattleResult.Weekly? get() = result?.weekly
}
