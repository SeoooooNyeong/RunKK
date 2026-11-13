package com.a207.runkk.app.ui.crew

/** 주간 목표 정하기(피그마 F-07)의 입력 묶음. */
data class GoalSetActions(val onBack: () -> Unit = {}, val onDraft: (String) -> Unit = {}, val onSave: () -> Unit = {})
