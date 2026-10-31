package com.a207.runkk.app.api.runimal

import kotlinx.serialization.Serializable

/**
 * 유형 상세(서버 RunimalController.TypeDetailResponse, D-285). sharePercent는 확정자가 없거나 계산 전이면 null,
 * computedAt은 +09:00 오프셋 시각(계산 전이면 null)이다.
 */
@Serializable
internal data class RunimalTypeResponse(
    val type: String,
    val displayName: String = "",
    val icon: String? = null,
    val description: String = "",
    val axes: Map<String, String> = emptyMap(),
    val traits: List<String> = emptyList(),
    val sharePercent: Int? = null,
    val computedAt: String? = null
)
