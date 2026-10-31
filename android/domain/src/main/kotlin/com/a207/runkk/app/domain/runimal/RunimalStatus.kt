package com.a207.runkk.app.domain.runimal

import com.a207.runkk.app.core.model.Outcome

/** Runimal 상태(서버 runimal.RunimalStatusView, FR-151~FR-154): 유형, 확정까지 남은 횟수, 카드, 16유형 목록. */
data class RunimalStatus(
    val type: String?,
    val isProvisional: Boolean,
    val remainingRuns: Int?,
    val card: RunimalCard?,
    val allTypes: List<String>,
    /** 지금까지 Runimal 판정에 반영한 유효 러닝 순번. 옛 서버면 null. 랜덤 대결 잠금은 이 값 대신 본인 요약의 유효 러닝 수를 쓴다(D-331). */
    val validRunCount: Int? = null
)
