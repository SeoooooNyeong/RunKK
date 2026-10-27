package com.a207.runkk.app.ui.me

/** 동의 화면의 사용자 행동 묶음(상태 없는 화면이 ViewModel을 모르게). onSubmit의 인자는 게스트로 시작하는가다. */
data class SignUpActions(
    val onBack: () -> Unit = {},
    val onAllAgreedChange: (Boolean) -> Unit = {},
    val onConsentChange: (String, Boolean) -> Unit = { _, _ -> },
    val onViewLegal: (String) -> Unit = {},
    val onLegalDismissed: () -> Unit = {},
    val onSubmit: (Boolean) -> Unit = {},
    val onGuestNoticeConfirmed: () -> Unit = {},
    val onGuestNoticeLink: () -> Unit = {},
    val onErrorShown: () -> Unit = {}
)
