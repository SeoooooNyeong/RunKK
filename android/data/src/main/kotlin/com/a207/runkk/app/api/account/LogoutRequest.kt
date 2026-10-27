package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

@Serializable
internal data class LogoutRequest(val refreshToken: String)
