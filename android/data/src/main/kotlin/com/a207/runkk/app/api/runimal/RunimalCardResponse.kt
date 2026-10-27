package com.a207.runkk.app.api.runimal

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** 서버 runimal.RunimalCardView(앞면만). axisRatios는 축 이름 → 우세 비율(0~1). */
@Serializable
internal data class RunimalCardResponse(
    val type: String,
    val title: String,
    val description: String,
    val axisRatios: Map<String, Double> = emptyMap()
)
