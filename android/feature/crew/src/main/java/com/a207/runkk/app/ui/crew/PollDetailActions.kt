package com.a207.runkk.app.ui.crew

/** 투표 화면(와이어프레임 M3)의 입력 묶음. */
data class PollDetailActions(
    val onBack: () -> Unit = {},
    val onRespond: (Int) -> Unit = {},
    val onDeleteAsk: () -> Unit = {},
    val onReport: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)
