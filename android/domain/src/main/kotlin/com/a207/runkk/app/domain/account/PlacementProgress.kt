package com.a207.runkk.app.domain.account

/**
 * 본인 배치고사 진행(GET /me/placement, FR-279~FR-282, D-488, 05-03 5.7.3.3). [countedRounds]는 센 판 수(0~3),
 * [nextRound]는 배치 중일 때 다음 판 번호(1~3), [provisionalTier]는 배치 중 1판 이상 센 뒤의 임시 티어(본인만, 브리프 규칙 11),
 * [tier]는 회원 티어(공개 티어, 배치 중이면 UNRANKED), [rounds]는 센 판(판 번호 오름차순).
 */
data class PlacementProgress(
    val status: PlacementStatus,
    val countedRounds: Int,
    val nextRound: Int?,
    val provisionalTier: String?,
    val tier: String,
    val rounds: List<PlacementRound>
)
