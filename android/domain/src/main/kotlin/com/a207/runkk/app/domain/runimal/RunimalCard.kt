package com.a207.runkk.app.domain.runimal

import com.a207.runkk.app.core.model.Outcome

/** Runimal 유형 카드(서버 runimal.RunimalCardView, 앞면만, FR-168). axisRatios는 축별 우세 비율이다. */
data class RunimalCard(
    val type: String,
    val title: String,
    val description: String,
    val axisRatios: Map<String, Double>
)
