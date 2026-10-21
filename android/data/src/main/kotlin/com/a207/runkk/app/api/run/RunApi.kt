package com.a207.runkk.app.api.run

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** 기록 전송·늦은 심박·파기 지시 REST(5.7.1 RunController). */
internal interface RunApi {
    @POST("runs")
    suspend fun upload(@Body body: RunUploadRequest): UploadResponse

    @POST("runs/{id}/heart-rates")
    suspend fun appendHeartRates(@Path("id") recordId: String, @Body body: LateHeartRatesRequest)

    @GET("me/disposals")
    suspend fun disposals(): List<DisposalOrderResponse>

    @POST("me/disposals/{id}/confirm")
    suspend fun confirmDisposal(@Path("id") orderId: Long)
}
