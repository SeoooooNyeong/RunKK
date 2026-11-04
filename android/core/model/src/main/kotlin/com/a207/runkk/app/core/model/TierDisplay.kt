package com.a207.runkk.app.core.model

/**
 * 티어를 화면에 보이는 방법(FR-282, D-494, 05-03 5.7.3.5). [tier]는 서버 Tier 이름(BRONZE~MASTER)이다.
 * 본인 화면(홈 헤더·마이·랜덤 대결·배치고사)은 [ofSelf], 다른 사람 자리(랭킹·다른 러너 프로필·매칭 상대)는 [ofPublic]을 쓴다.
 * 임시 티어는 본인에게만 보인다(브리프 규칙 11): [ofPublic]은 [Provisional]을 만들지 않는다.
 */
sealed interface TierDisplay {
    /** 확정 티어. 모르는 값도 그대로 담고 그림은 배치 중 그림이다(TierEmblem). */
    data class Confirmed(val tier: String) : TierDisplay

    /** 배치고사 중 임시 티어(1·2판을 센 뒤, 본인만). */
    data class Provisional(val tier: String) : TierDisplay

    /** 확정 티어가 없다(서버 UNRANKED, 화면 「배치 중」). */
    data object Placing : TierDisplay

    companion object {
        /** 서버의 확정 전 티어 이름. */
        const val UNRANKED = "UNRANKED"

        /** 본인: 확정 티어가 있으면 확정, 배치 중이면 임시 티어가 있을 때 임시, 없으면 배치 중. 티어를 모르면 null. */
        fun ofSelf(tier: String?, provisionalTier: String?): TierDisplay? = when {
            tier.isNullOrBlank() -> null
            tier != UNRANKED -> Confirmed(tier)
            !provisionalTier.isNullOrBlank() && provisionalTier != UNRANKED -> Provisional(provisionalTier)
            else -> Placing
        }

        /** 다른 사람(공개 티어): 확정 티어 아니면 배치 중. 티어를 모르면 null(그리지 않는다). */
        fun ofPublic(tier: String?): TierDisplay? = ofSelf(tier, provisionalTier = null)
    }
}
