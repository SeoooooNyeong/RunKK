package com.a207.runkk.app.domain.runimal

import com.a207.runkk.app.core.model.Outcome

/** Runimal 조회 포트(GET /runimal, GET /runimal/card). 구현 api.runimal. */
interface RunimalRepository {
    suspend fun status(): Outcome<RunimalStatus>

    /** 유형이 없으면 Failure(Rejected(NO_TYPE))(FR-168). */
    suspend fun card(): Outcome<RunimalCard>
}

/** Runimal 상태(서버 runimal.RunimalStatusView, FR-151~FR-154): 유형, 확정까지 남은 횟수, 카드, 16유형 목록. */
data class RunimalStatus(
    val type: String?,
    val provisional: Boolean,
    val remainingRuns: Int?,
    val card: RunimalCard?,
    val allTypes: List<String>
)

/** Runimal 유형 카드(서버 runimal.RunimalCardView, 앞면만, FR-168). axisRatios는 축별 우세 비율이다. */
data class RunimalCard(
    val type: String,
    val title: String,
    val description: String,
    val axisRatios: Map<String, Double>
)
