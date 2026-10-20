package com.a207.runkk.app.api.live

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 실시간 폴링 대체 REST(5.7.1 LivePollingController, C-004). WebSocket을 쓸 수 없을 때만 data.live.PollingLiveChannel이 부른다.
 * 메시지 본문은 WebSocket과 같은 JSON이고 해독·진행값 변환은 data.live가 한다(그래서 이 패키지는 형식을 모른다).
 */
interface LiveApi {
    /** 그동안 쌓인 서버 → 앱 메시지(서버 PollingMessenger.drain). */
    @GET("live/poll")
    suspend fun poll(): JsonArray

    /** 앱 → 서버 진행값(InboundProgress JSON). */
    @POST("live/progress")
    suspend fun progress(@Body body: JsonObject)
}
