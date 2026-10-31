package com.a207.runkk.app.ui.runimal

import androidx.annotation.StringRes

/** 평가 결과 화면의 티어 이름(서버 Tier 이름 → 한국어). UNRANKED와 모르는 값은 없다(받은 값을 그대로 보인다). */
enum class EvaluationTier(@StringRes val nameRes: Int) {
    BRONZE(R.string.evaluation_tier_bronze),
    SILVER(R.string.evaluation_tier_silver),
    GOLD(R.string.evaluation_tier_gold),
    PLATINUM(R.string.evaluation_tier_platinum),
    MASTER(R.string.evaluation_tier_master);

    companion object {
        fun of(tier: String): EvaluationTier? = entries.firstOrNull { it.name == tier }
    }
}
