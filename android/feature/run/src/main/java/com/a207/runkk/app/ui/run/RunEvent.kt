package com.a207.runkk.app.ui.run

sealed interface RunEvent {
    /** 저장됨: 결과 화면으로. */
    data class Finished(val recordId: String) : RunEvent

    /** 거리 0이라 저장하지 않음: 홈으로. */
    data object NotSaved : RunEvent

    /** 카운트다운 취소: 이전 화면으로. */
    data object Exit : RunEvent
}
