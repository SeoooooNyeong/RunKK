package com.a207.runkk.app.ui.me

/** 설정 화면 한 번짜리 이벤트. */
sealed interface SettingsEvent {
    /** 로그아웃 끝(FR-002): 로그인 화면으로. */
    data object LoggedOut : SettingsEvent
}
