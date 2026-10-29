package com.a207.runkk.app.ui.home

/** 홈 화면이 한 번 처리하는 사건. */
sealed interface HomeEvent {
    /** 위치 동의가 확인됐으니 자유런을 시작한다(OS 위치 권한이 없으면 화면이 권한 창을 먼저 띄운다, D-267). */
    data object StartRun : HomeEvent
}
