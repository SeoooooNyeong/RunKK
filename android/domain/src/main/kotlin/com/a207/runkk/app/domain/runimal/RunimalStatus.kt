package com.a207.runkk.app.domain.runimal

import com.a207.runkk.app.core.model.Outcome

/** Runimal 상태(서버 runimal.RunimalStatusView, FR-151~FR-154): 유형, 확정까지 남은 횟수, 카드, 16유형 목록. */
data class RunimalStatus(
    val type: String?,
    val isProvisional: Boolean,
    val remainingRuns: Int?,
    val card: RunimalCard?,
    val allTypes: List<String>,
    /** 지금까지 유효 러닝 횟수. 옛 서버면 null. 홈이 랜덤 대결(유효 3회 뒤)까지 남은 횟수를 셀 때 쓴다. */
    val validRunCount: Int? = null
)
