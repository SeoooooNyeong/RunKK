package com.a207.runkk.app.api.run

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** 늦은 심박·파기 지시 REST(5.7.1 RunController). 기록 전송(POST /runs)은 업로드 전용 클라이언트의 [RunUploadApi]다(Jira 327). */
internal interface RunApi {
    @POST("runs/{id}/heart-rates")
    suspend fun appendHeartRates(@Path("id") recordId: String, @Body body: LateHeartRatesRequest)

    @GET("me/disposals")
    suspend fun disposals(): List<DisposalOrderResponse>

    @POST("me/disposals/{id}/confirm")
    suspend fun confirmDisposal(@Path("id") orderId: Long)
}
