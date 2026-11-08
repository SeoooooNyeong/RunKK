package com.a207.runkk.app.api.notification

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

/** 이벤트 REST(이벤트 계약 2절)와 이벤트 그림(서버 정적 파일 /events/{slug}/…). */
internal interface EventApi {
    @GET("events")
    suspend fun events(): EventsResponse

    @GET("events/{slug}/me")
    suspend fun me(@Path("slug") slug: String): EventEntryResponse

    /** [url]은 절대 주소다(API origin에 붙인 이벤트 그림). */
    @Streaming
    @GET
    suspend fun image(@Url url: String): ResponseBody
}
