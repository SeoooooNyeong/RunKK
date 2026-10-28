package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.battle.ActiveBattle

/** 서버에 남은 진행 중 대결 안내. [battle]이 있으면 대화상자를 띄운다. [error]는 그만두기 실패 문구다. */
data class ActiveBattleUiState(
    val battle: ActiveBattle? = null,
    val isForfeiting: Boolean = false,
    val error: UiText? = null
)
