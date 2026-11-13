package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.model.Gender

/**
 * 가입·게스트 공용 화면의 사용자 행동 묶음(상태 없는 화면이 ViewModel을 모르게). [onBack]은 단계를 따라 돌아가고(성별 → 동의 → 로그인),
 * [onExit]는 바로 로그인으로 간다. [onStart]의 인자는 게스트로 시작하는가다.
 */
data class SignUpActions(
    val onBack: () -> Unit = {},
    val onExit: () -> Unit = {},
    val onAllAgreedChange: (Boolean) -> Unit = {},
    val onConsentChange: (String, Boolean) -> Unit = { _, _ -> },
    val onViewLegal: (String) -> Unit = {},
    val onLegalDismissed: () -> Unit = {},
    /** 동의 단계의 시작하기: 성별 단계로. */
    val onConsentNext: () -> Unit = {},
    val onGenderSelect: (Gender) -> Unit = {},
    /** 성별 단계의 시작하기: 가입(게스트 시작) 뒤 성별 저장. */
    val onStart: (Boolean) -> Unit = {},
    val onGuestNoticeConfirmed: () -> Unit = {},
    val onGuestNoticeLink: () -> Unit = {},
    val onErrorShown: () -> Unit = {}
)
