package com.a207.runkk.app.domain.record

/**
 * 그 러닝이 일으킨 티어 평가(서버 EvaluationDto, D-284·D-291·D-294). [tier]는 서버 Tier 이름, [validRuns]는 평가를 일으킨
 * 유효 러닝 순번(첫 배치 3, 재평가 10의 배수), [runimalType]은 그 판정에서 확정된 Runimal 유형(첫 배치·미확정이면 null)이다.
 * [previousTier]는 재평가 직전 티어 이름이다(과거 기록·옛 서버·첫 배치면 null).
 */
data class Evaluation(
    val kind: Kind,
    val tier: String,
    val validRuns: Int,
    val runimalType: String?,
    val previousTier: String? = null
) {
    enum class Kind {
        FIRST_PLACEMENT,
        REEVALUATION
    }
}
