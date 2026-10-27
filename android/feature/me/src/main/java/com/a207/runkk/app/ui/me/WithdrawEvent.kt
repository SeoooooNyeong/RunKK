package com.a207.runkk.app.ui.me

sealed interface WithdrawEvent {
    data object Withdrawn : WithdrawEvent
}
