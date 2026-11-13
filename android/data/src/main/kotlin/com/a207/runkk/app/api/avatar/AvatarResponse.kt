package com.a207.runkk.app.api.avatar

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query

/** 서버 web.avatar.AvatarResponse. equipped·rejected 키는 착용 부위(ItemSlot) 이름이다. */
@Serializable
internal data class AvatarResponse(
    val faceKey: String? = null,
    val defaultCharacterId: String? = null,
    val bodyShape: String,
    val skinTone: String,
    val equipped: Map<String, Long> = emptyMap(),
    val balance: Int = 0,
    val canCreateSelfieToday: Boolean = false,
    val rejected: Map<String, String> = emptyMap(),
    val backgroundKey: String? = null
)
