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
 * GET /avatar/backgrounds·PUT /avatar/background 응답(서버 AvatarDtos.BackgroundsResponse, D-107). selectedKey가 null이면 기본 배경,
 * stage는 DOMESTIC·EAST_ASIA·INTERCONTINENTAL, unlockedAt은 오프셋 시각(잠겼으면 null)이다.
 */
@Serializable
internal data class BackgroundsResponse(
    val selectedKey: String? = null,
    val backgrounds: List<Background> = emptyList()
) {
    @Serializable
    data class Background(
        val key: String,
        val name: String,
        val stage: String,
        val distanceMeters: Long,
        val unlocked: Boolean = false,
        val unlockedAt: String? = null
    )
}
