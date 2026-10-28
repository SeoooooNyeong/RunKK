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
