package com.a207.runkk.app.ui.battle

import androidx.navigation3.runtime.NavKey

/** 배치고사 진행 화면의 한 번 처리 이동. 경로 이름 키(확정 티어 → 랜덤 대결)는 이 화면을 바꿔 열고, 매칭은 위에 연다. */
sealed interface PlacementEvent {
    data class Open(val key: NavKey) : PlacementEvent
}
