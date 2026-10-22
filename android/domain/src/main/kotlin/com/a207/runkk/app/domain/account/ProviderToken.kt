package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance

/** 제공자 SDK로 받은 토큰. ui.me의 SocialLoginClient가 만들고 LoginUseCase에 넘긴다(D-19). */
data class ProviderToken(val provider: ProviderType, val token: String) {
    override fun toString(): String = "ProviderToken(provider=$provider, token=***)"
}
