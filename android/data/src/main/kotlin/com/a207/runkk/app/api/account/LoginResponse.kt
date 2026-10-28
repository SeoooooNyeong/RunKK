package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/**
 * POST /auth/login 응답(서버 LoginResult). 회원이면 토큰 쌍, 가입 전이면 signUpTicket(15분, D-19)만 온다.
 * 실패(제공자 무응답·인증 실패)는 409 + 사유 코드로 온다.
 */
@Serializable
internal data class LoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val signUpTicket: String? = null
)
