package com.a207.runkk.app.api.runimal

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** Runimal 조회 REST(5.7.1 RunimalController). */
internal interface RunimalApi {
    @GET("runimal")
    suspend fun status(): RunimalStatusResponse

    /** 유형이 없으면 409 + 사유 코드(NO_TYPE)다(FR-168). */
    @GET("runimal/card")
    suspend fun card(): RunimalCardResponse
}
