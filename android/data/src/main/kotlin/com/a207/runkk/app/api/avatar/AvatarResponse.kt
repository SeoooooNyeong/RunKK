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

/**
 * 서버 web.avatar.AvatarResponse. equipped·rejected 키는 착용 부위(ItemSlot) 이름이다. 셀카 요청이면 변환이 실패해도 200으로 오고
 * created=false와 failureReason(NO_FACE·MULTIPLE_FACES·UNSUPPORTED_IMAGE·TOO_LARGE·UPSTREAM_FAILED·TIMEOUT)이 함께 온다.
 */
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
    val created: Boolean? = null,
    val failureReason: String? = null,
    val backgroundKey: String? = null
)
