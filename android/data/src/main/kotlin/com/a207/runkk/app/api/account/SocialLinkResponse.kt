package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/** POST /me/social-account 응답(서버 AuthController.SocialLinkResponse): accountType LINKED와 연동한 제공자. */
@Serializable
internal data class SocialLinkResponse(val accountType: String? = null, val provider: String? = null)
