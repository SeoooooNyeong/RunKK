package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.domain.crew.AttendanceAnswer

/** 대면 모임 상세(와이어프레임 M7)의 입력 묶음. */
data class MeetupDetailActions(
    val onBack: () -> Unit = {},
    val onAnswer: (AttendanceAnswer) -> Unit = {},
    val onDeleteAsk: () -> Unit = {},
    val onReport: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)
