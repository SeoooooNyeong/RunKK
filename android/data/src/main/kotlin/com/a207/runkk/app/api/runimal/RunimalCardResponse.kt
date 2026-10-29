package com.a207.runkk.app.api.runimal

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** 서버 runimal.RunimalCardView(앞면만). axisRatios는 축 이름 → 우세 비율(0~1). */
@Serializable
internal data class RunimalCardResponse(
    val type: String,
    val title: String,
    val description: String,
    val axisRatios: Map<String, Double> = emptyMap(),
    /** 축 막대(v0.14 MY Runimal, D-285). 옛 서버는 주지 않는다(D-289). */
    val axes: List<AxisDto> = emptyList()
) {
    /** 축 막대 하나(서버 AxisResponse): 축, 우세 성향 이름(예: 솔로런), 그 비율(%). */
    @Serializable
    data class AxisDto(val axis: String, val label: String, val percent: Int = 0)
}
