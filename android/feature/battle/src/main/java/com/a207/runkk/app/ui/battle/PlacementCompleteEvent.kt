package com.a207.runkk.app.ui.battle

/** 완료 화면의 한 번 처리 이동. 「랜덤 대결 하러 가기」는 홈 위에 랜덤 대결, 「홈으로」·뒤로는 홈이다. */
sealed interface PlacementCompleteEvent {
    data object RandomBattle : PlacementCompleteEvent

    data object Home : PlacementCompleteEvent
}
