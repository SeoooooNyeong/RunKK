package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/** POST /auth/login 요청(서버 AuthService.login(provider, providerToken)). */
@Serializable
internal data class LoginRequest(val provider: String, val providerToken: String)
