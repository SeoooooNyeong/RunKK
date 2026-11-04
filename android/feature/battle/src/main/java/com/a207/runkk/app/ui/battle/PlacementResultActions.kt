package com.a207.runkk.app.ui.battle

/** 판 결과 화면의 사용자 행동. */
data class PlacementResultActions(
    val onBack: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onRetry: () -> Unit = {}
)
