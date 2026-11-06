package com.a207.runkk.app.ui.battle

import androidx.navigation3.runtime.NavKey

/** 친구 대결 화면의 한 번 처리 이동(초대 대기). 위치 권한을 확인한 뒤 연다. */
sealed interface FriendBattleEvent {
    data class Open(val key: NavKey) : FriendBattleEvent
}
