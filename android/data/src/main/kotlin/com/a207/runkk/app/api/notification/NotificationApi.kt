package com.a207.runkk.app.api.notification

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** 알림함·푸시 기기 토큰 REST(5.7.1 NotificationController, D-43). */
internal interface NotificationApi {
    @GET("notifications")
    suspend fun inbox(): InboxResponse

    @POST("notifications/{id}/open")
    suspend fun open(@Path("id") notificationId: Long)

    @PUT("me/push-token")
    suspend fun registerPushToken(@Body body: PushTokenRequest)
}

/**
 * 서버 notification.InboxView(최신순). 서버 항목 이름은 type·content·occurredAt·read·target(화면 종류 screen, 대상 targetId)이다.
 * 앱은 제목을 알림 종류에서, 이동 경로를 target.screen에서 만든다(NotificationRemotes).
 */
@Serializable
internal data class InboxResponse(val items: List<Item> = emptyList()) {
    @Serializable
    data class Item(
        val id: Long,
        val type: String,
        val content: String,
        val occurredAt: String,
        val read: Boolean = false,
        val target: Target? = null
    )

    @Serializable
    data class Target(val screen: String, val targetId: String? = null)
}

@Serializable
internal data class PushTokenRequest(val token: String)
