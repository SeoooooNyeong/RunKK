package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.account.ProviderType

/** 로그인 화면 상태. [inProgress]는 지금 로그인 중인 제공자. */
data class LoginUiState(val inProgress: ProviderType? = null, val error: UiText? = null)
