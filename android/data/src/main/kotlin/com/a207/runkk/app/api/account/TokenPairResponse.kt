package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

@Serializable
internal data class TokenPairResponse(val accessToken: String, val refreshToken: String)
