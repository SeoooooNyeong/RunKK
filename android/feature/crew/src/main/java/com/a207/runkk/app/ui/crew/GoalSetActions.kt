package com.a207.runkk.app.ui.crew

/** 주간 공동 목표 정하기(와이어프레임 M8)의 입력 묶음. */
data class GoalSetActions(
    val onBack: () -> Unit = {},
    val onDraft: (String) -> Unit = {},
    val onQuick: (Int) -> Unit = {},
    val onSave: () -> Unit = {}
)
