package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.battle.BattleHistory

data class BattleHistoryUiState(
    val isLoading: Boolean = true,
    val filterIndex: Int = 0,
    val history: BattleHistory? = null,
    /** 다음 쪽을 읽는 중. */
    val isLoadingMore: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
)
