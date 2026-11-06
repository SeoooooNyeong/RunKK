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
) {
    /**
     * 배치고사를 마쳤는데 이 기기에서 그 완료 화면(FR-281)을 아직 안 봤는가(D-531, PL3-A1). [seen]은 본 마지막 판의 대결 ID
     * (AccountRepository.placementCompleteSeen)이고, 완료이고 센 판 가운데 판 번호가 가장 큰 판의 대결 ID가 그것과 다르면 참이다.
     * 배치고사 진행 화면과 랜덤 대결 화면이 같은 식으로 완료 화면을 연다.
     */
    fun isCompletionUnseen(seen: Long?): Boolean {
        val last = rounds.maxByOrNull { it.round }?.battleId ?: return false
        return status == PlacementStatus.COMPLETED && seen != last
    }
}
