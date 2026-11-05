package com.a207.runkk.watch.battle

/**
 * 결과 문구에 쓰는 본인 배치고사 진행(GET /me/placement의 PlacementProgress를 watch.ui가 옮긴 값, 워치 배치고사). [isCompleted]는
 * 배치 중이 아님(완료·면제), [countedRounds]는 센 판 수, [provisionalTier]는 배치 중 임시 티어, [tier]는 회원 티어(서버 Tier 이름)다.
 */
data class PlacementStanding(
    val isCompleted: Boolean,
    val countedRounds: Int,
    val provisionalTier: String?,
    val tier: String
)
