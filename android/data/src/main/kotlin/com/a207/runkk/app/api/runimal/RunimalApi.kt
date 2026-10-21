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

/** 서버 runimal.RunimalStatusView. remainingRuns는 유형 확정까지 남은 유효 러닝 수. */
@Serializable
internal data class RunimalStatusResponse(
    val type: String? = null,
    val provisional: Boolean = false,
    val remainingRuns: Int? = null,
    val card: RunimalCardResponse? = null,
    val allTypes: List<String> = emptyList()
)

/** 서버 runimal.RunimalCardView(앞면만). axisRatios는 축 이름 → 우세 비율(0~1). */
@Serializable
internal data class RunimalCardResponse(
    val type: String,
    val title: String,
    val description: String,
    val axisRatios: Map<String, Double> = emptyMap()
)
