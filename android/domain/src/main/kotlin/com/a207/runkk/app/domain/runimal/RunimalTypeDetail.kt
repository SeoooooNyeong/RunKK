package com.a207.runkk.app.domain.runimal

import java.time.Instant

/**
 * 유형 상세(GET /runimal/types/{type}, D-285). [traits]는 특징 3줄, [sharePercent]는 러너 중 이 유형 비율(%)이고 확정자가 없거나
 * 계산 전이면 null, [computedAt]은 비율 계산 시각(계산 전이면 null)이다.
 */
data class RunimalTypeDetail(
    val type: String,
    val displayName: String,
    val icon: String?,
    val description: String,
    val axes: Map<String, AxisLean>,
    val traits: List<String>,
    val sharePercent: Int?,
    val computedAt: Instant?
)
