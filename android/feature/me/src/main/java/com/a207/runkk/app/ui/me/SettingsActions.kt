package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.account.ProviderType

/** 설정 화면 사용자 행동. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val setValue: (String, String) -> Unit = { _, _ -> },
    val setConsent: (String, Boolean) -> Unit = { _, _ -> },
    val onViewLegal: (String, UiText) -> Unit = { _, _ -> },
    val onLegalDismissed: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onLink: (ProviderType) -> Unit = {},
    /** 다른 화면으로(알림 설정, 소셜 계정 연동, 회원 탈퇴). */
    val onNavigate: (String) -> Unit = {},
    /** 로그아웃 확인 대화상자에서 "로그아웃". */
    val onLogout: () -> Unit = {},
    /** 못 올린 기록 "다시 보내기"(W40). */
    val onResendRejected: () -> Unit = {},
    /** 「가이드 다시 보기」([임시:FR-성-36]): 홈으로 가서 첫 사용 가이드를 1단계부터 다시 본다. */
    val onReplayGuide: () -> Unit = {}
)
