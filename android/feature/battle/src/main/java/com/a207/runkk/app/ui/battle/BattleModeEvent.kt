package com.a207.runkk.app.ui.battle

import androidx.navigation3.runtime.NavKey

/** 배틀런 화면의 한 번 처리 이동. 화면이 OS 위치 권한을 확인한 뒤 [key]로 간다. */
sealed interface BattleModeEvent {
    data class Open(val key: NavKey) : BattleModeEvent
}
