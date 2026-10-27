package com.a207.runkk.app.ui.me

sealed interface SignUpEvent {
    /** 가입·게스트 시작 완료: 홈으로. 아바타는 나중에 아바타 화면에서 정한다(FR-003, FR-155). */
    data object Completed : SignUpEvent

    /** 게스트 시작 뒤 안내에서 "지금 연동하기": 홈을 거쳐 소셜 계정 연동(F9)으로(FR-198, FR-199). */
    data object CompletedToLink : SignUpEvent

    /** 뒤로: 로그인으로. */
    data object Exit : SignUpEvent

    /** 고지문서가 주소면 밖(브라우저)에서 연다. */
    data class OpenUrl(val url: String) : SignUpEvent
}
