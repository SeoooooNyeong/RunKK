package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/** POST /me/social-account 요청(서버 AuthController.SocialLinkRequest, FR-198). provider는 KAKAO·GOOGLE. */
@Serializable
internal data class SocialLinkRequest(val provider: String, val providerToken: String)
