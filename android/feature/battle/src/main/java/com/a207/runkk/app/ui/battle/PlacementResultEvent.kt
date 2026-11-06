package com.a207.runkk.app.ui.battle

import androidx.navigation3.runtime.NavKey

/** 판 결과 화면의 한 번 처리 이동. 「다음 판 뛰기」는 홈 위에 배치고사 진행 하나만 남기고(뒤 스택 정리), 「홈으로」·뒤로는 홈이다. */
sealed interface PlacementResultEvent {
    /** 이 화면을 [key]로 바꿔 연다(3판째를 셈 → 배치고사 완료). */
    data class Replace(val key: NavKey) : PlacementResultEvent

    data object NextRound : PlacementResultEvent

    data object Home : PlacementResultEvent
}
