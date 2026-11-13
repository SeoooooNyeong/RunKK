package com.a207.runkk.app.api.coach

import com.a207.runkk.app.api.core.BoundSession
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Tag

internal interface CoachApi {
    @GET("coach/conversations")
    suspend fun conversations(@Tag session: BoundSession): List<Conversation>

    @GET("coach/conversations/{conversationId}/messages")
    suspend fun messages(@Tag session: BoundSession, @Path("conversationId") conversationId: String): List<Message>

    @Streaming
    @Headers("Accept: text/event-stream")
    @POST("coach/questions")
    fun ask(@Tag session: BoundSession, @Body question: Question): Call<ResponseBody>

    @Serializable
    data class Question(val conversationId: String?, val text: String)

    @Serializable
    data class Conversation(val conversationId: String, val firstQuestion: String, val lastAskedAt: String)

    /** role은 QUESTION(내 질문)·ANSWER(코치 답), at은 서버 시각(LocalDateTime 문자열). */
    @Serializable
    data class Message(val role: String, val text: String, val at: String)
}
