package com.a207.runkk.app.api.chat

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 크루 채팅 REST(5.7.1 ChatController, F.2.2 크루 채팅, D-101). 받기는 실시간 채널이 하고 보내기·조회·삭제·리액션은 REST다. */
internal interface ChatApi {
    /** before·after는 메시지 ID 커서이고 둘 중 하나만 보낸다(둘 다면 400). 목록은 늘 오래된 → 새 순이다. */
    @GET("crews/{id}/chat/messages")
    suspend fun history(
        @Path("id") crewId: Long,
        @Query("before") before: Long?,
        @Query("after") after: Long?,
        @Query("size") size: Int
    ): ChatPageResponse

    /** 201(새 메시지) 또는 200(같은 clientMessageId 재전송, 처음 만든 메시지). */
    @POST("crews/{id}/chat/messages")
    suspend fun post(@Path("id") crewId: Long, @Body body: ChatPostRequest): ChatMessageItem

    @DELETE("chat-messages/{id}")
    suspend fun delete(@Path("id") messageId: Long)

    @PUT("chat-messages/{id}/reactions/{kind}")
    suspend fun react(@Path("id") messageId: Long, @Path("kind") kind: String): ReactionResponse

    @DELETE("chat-messages/{id}/reactions/{kind}")
    suspend fun unreact(@Path("id") messageId: Long, @Path("kind") kind: String): ReactionResponse
}
