package com.a207.runkk.app.api.notification

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
internal data class PushTokenRequest(val token: String)
