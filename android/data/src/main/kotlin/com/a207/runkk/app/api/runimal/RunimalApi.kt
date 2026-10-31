package com.a207.runkk.app.api.runimal

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/** Runimal 조회 REST(5.7.1 RunimalController). */
internal interface RunimalApi {
    @GET("runimal")
    suspend fun status(): RunimalStatusResponse

    /** 유형이 없으면 409 + 사유 코드(NO_TYPE)다(FR-168). */
    @GET("runimal/card")
    suspend fun card(): RunimalCardResponse

    /** 16유형과 유형마다 확정받은 적 있는지(D-285, 서버 D-245). */
    @GET("runimal/types")
    suspend fun types(): RunimalTypesResponse

    /** 유형 상세. 모르는 유형은 400이다(서버 D-246). */
    @GET("runimal/types/{type}")
    suspend fun type(@Path("type") type: String): RunimalTypeResponse
}
