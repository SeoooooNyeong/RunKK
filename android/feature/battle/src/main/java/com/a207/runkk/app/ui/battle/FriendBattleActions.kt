package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance

/** 친구 대결 화면의 사용자 행동. */
data class FriendBattleActions(
    val onFriend: (Long) -> Unit = {},
    val onDistanceMenu: () -> Unit = {},
    val onDistance: (Distance) -> Unit = {},
    val onInvite: () -> Unit = {},
    val onConsentAgree: () -> Unit = {},
    val onConsentClose: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)
