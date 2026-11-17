package com.a207.runkk.app.ui.battle

/** 매칭 화면의 사용자 행동. */
data class MatchActions(
    val onCancel: () -> Unit = {},
    val onForfeit: () -> Unit = {},
    val onResend: () -> Unit = {},
    val onAccept: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onInviteEndedConfirmed: () -> Unit = {},
    val onNoticeConfirmed: () -> Unit = {},
    val onConsentAgree: () -> Unit = {},
    val onConsentClose: () -> Unit = {},
    val onLeaveConfirm: () -> Unit = {},
    val onLeaveDismiss: () -> Unit = {}
)
