package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText

data class WithdrawUiState(
    val isAcknowledged: Boolean = false,
    val isWithdrawing: Boolean = false,
    val error: UiText? = null
)
