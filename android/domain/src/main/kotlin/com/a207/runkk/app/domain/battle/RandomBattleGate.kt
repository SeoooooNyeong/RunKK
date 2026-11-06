package com.a207.runkk.app.domain.battle

/**
 * 랜덤 대결 배치고사 상수(D-500, FR-257, J24-12). 폰의 "유효 러닝 3회 뒤" 잠금은 없앴다(FR-274, CR-139): 배치 중(UNRANKED) 회원의 랜덤
 * 대결은 배치고사 3판이다. 워치도 배치 중이라고 잠그지 않고 배치고사를 한다(S15P31A207-275): 옛 서버가 RANKED_ONLY로 거부할 때만 워치가
 * 잠금 안내를 보인다. [PLACEMENT_RUNS]는 서버 값의 사본이다(H-44): 계약 시험이 android/contract/policy/TierPlacement.txt(서버
 * member.PlacementProgress.ROUNDS)와 대조한다(D-509).
 */
object RandomBattleGate {
    /** 배치고사 판 수(서버 member.PlacementProgress.ROUNDS). 진행 「n/3」의 분모다. */
    const val PLACEMENT_RUNS = 3

    /** 배치 중 티어 이름(서버 common.Tier.UNRANKED). */
    const val UNRANKED = "UNRANKED"
}
