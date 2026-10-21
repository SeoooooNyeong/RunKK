package com.a207.runkk.app.domain.runimal

import com.a207.runkk.app.core.model.Outcome

/** Runimal 상태(서버 runimal.RunimalStatusView, FR-151~FR-154): 유형, 확정까지 남은 횟수, 카드, 16유형 목록. */
data class RunimalStatus(
    val type: String?,
    val isProvisional: Boolean,
    val remainingRuns: Int?,
    val card: RunimalCard?,
    val allTypes: List<String>
)
