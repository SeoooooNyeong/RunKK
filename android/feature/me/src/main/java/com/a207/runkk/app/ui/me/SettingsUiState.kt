package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.ProviderType

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: MemberSettings? = null,
    val error: UiText? = null,
    val message: UiText? = null,
    val legal: LegalView? = null,
    val urlToOpen: String? = null,
    /** 연동하지 않은 게스트인가(계정 유형, 소셜 계정 연동, 로그아웃 확인, FR-198, FR-199). */
    val isGuest: Boolean = false,
    /** 지금 연동 중인 제공자. */
    val linkingProvider: ProviderType? = null,
    /** 내 닉네임(소셜 계정 연동 화면 위 칸). 못 읽었으면 null. */
    val nickname: String? = null,
    /** 연동 거부·실패(소셜 계정 연동 화면 안 한 줄). */
    val linkError: UiText? = null,
    val isLoggingOut: Boolean = false,
    val isSavingLlmConsent: Boolean = false,
    /** 서버가 거부해 자동 전송이 멈춘 폰 기록 수(W40). 0이면 기록 전송 묶음을 보이지 않는다. 워치 건수는 세지 않는다. */
    val rejectedRunCount: Int = 0,
    /** 다시 보내기를 처리하는 중(거듭 누름 막기). */
    val isResending: Boolean = false
) {
    /** 항목 값. 서버 값이 없으면 기본값. */
    fun value(key: String): String = settings?.values?.get(key) ?: SettingKeys.DEFAULTS[key] ?: "true"

    fun flag(key: String): Boolean = value(key).toBoolean()

    /** 동의 최근 값. 이력이 없으면 거짓(서버 ConsentHistory.isAgreed). */
    fun consent(item: String): Boolean = settings?.consents?.get(item) == true
}
