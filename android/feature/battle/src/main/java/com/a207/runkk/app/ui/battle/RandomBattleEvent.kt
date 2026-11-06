package com.a207.runkk.app.ui.battle

import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.Routes

/** 랜덤 대결 화면의 한 번 처리 이동. 경로 이름 키(Routes.Key)는 이 화면을 바꿔 열고, 매칭은 위치 권한을 확인한 뒤 위에 연다. */
sealed interface RandomBattleEvent {
    data class Open(val key: NavKey) : RandomBattleEvent
}
