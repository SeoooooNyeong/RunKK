package com.a207.runkk.app.domain.battle

/**
 * 랜덤 대결 잠금 판단(D-500, FR-257, J24-12). 폰의 "유효 러닝 3회 뒤" 잠금은 없앴다(FR-274, CR-139): 배치 중(UNRANKED) 회원의 폰 랜덤 대결은
 * 배치고사 3판이다. 배치고사 화면(진행·판 결과·완료)은 폰에만 있어 워치 랜덤 대결만 배치 중일 때 잠근다.
 * [PLACEMENT_RUNS]는 서버 값의 사본이다(H-44): 계약 시험이 android/contract/policy/TierPlacement.txt(서버 member.PlacementProgress.ROUNDS)와
 * 대조한다(D-509).
 */
object RandomBattleGate {
    /** 배치고사 판 수(서버 member.PlacementProgress.ROUNDS). 진행 「n/3」의 분모다. */
    const val PLACEMENT_RUNS = 3

    /** 배치 중 티어 이름(서버 common.Tier.UNRANKED). */
    const val UNRANKED = "UNRANKED"

    /** 워치 랜덤 대결을 잠그는가: 배치 중(UNRANKED)일 때만 참. [tier]를 모르면(null) 잠그지 않는다(서버가 배치고사로 받는다). */
    fun isWatchRandomLocked(tier: String?): Boolean = tier == UNRANKED
}
