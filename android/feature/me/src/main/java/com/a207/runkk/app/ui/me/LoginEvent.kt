package com.a207.runkk.app.ui.me

sealed interface LoginEvent {
    data object LoggedIn : LoginEvent

    /** 회원이 아니면 단계별 가입으로(FR-001 "가입필요"). */
    data object SignUpRequired : LoginEvent
}
