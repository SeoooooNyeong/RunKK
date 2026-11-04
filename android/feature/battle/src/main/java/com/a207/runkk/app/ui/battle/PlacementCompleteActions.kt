package com.a207.runkk.app.ui.battle

/** 완료 화면의 사용자 행동. */
data class PlacementCompleteActions(
    val onBack: () -> Unit = {},
    val onRandom: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onRetry: () -> Unit = {}
)
