package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance

/** 배치고사 진행 화면의 사용자 행동. */
data class PlacementActions(
    val onBack: () -> Unit = {},
    val onDistance: (Distance) -> Unit = {},
    val onStart: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onConsentAgree: () -> Unit = {},
    val onConsentClose: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)
