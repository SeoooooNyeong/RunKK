package com.a207.runkk.member;

import com.a207.runkk.common.Tier;

/**
 * 유효 러닝 한 회가 일으킨 티어 평가(v0.14 D-291·D-294). 평가 시점 규칙의 주인인 TierProgress가 정한다.
 *
 * @param kind 첫 배치(유효 3회째) 또는 재평가(10의 배수 회차)
 * @param validRuns 평가를 일으킨 유효 러닝 순번(첫 배치 3, 재평가 10의 배수)
 * @param previousTier 평가가 바꾸기 전 티어(Member.recordValidRun이 담는다, v0.14 [임시:D-성-972]), 모르면 null
 */
public record TierEvaluation(EvaluationKind kind, int validRuns, Tier previousTier) {

    /** 직전 티어를 모르는 평가(평가 시점 계산만 하는 TierProgress.evaluationBetween). */
    public TierEvaluation(EvaluationKind kind, int validRuns) {
        this(kind, validRuns, null);
    }
}
