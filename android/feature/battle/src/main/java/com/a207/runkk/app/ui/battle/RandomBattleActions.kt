package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance

/** 랜덤 대결 화면의 사용자 행동. */
data class RandomBattleActions(
    val onBack: () -> Unit = {},
    val onDistance: (Distance) -> Unit = {},
    val onStart: () -> Unit = {},
    val onConsentAgree: () -> Unit = {},
    val onConsentClose: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)
