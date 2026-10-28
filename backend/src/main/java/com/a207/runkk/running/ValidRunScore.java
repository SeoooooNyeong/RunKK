package com.a207.runkk.running;

import com.a207.runkk.common.Distance;

/**
 * 티어에 넘길 유효 기록 하나(설계 6.1.3, D-108). RunQueryService.validRunsAfter의 원소이고 TierReconcileService가
 * RunScoreInput(score, distance, ordinal)으로 바꿔 넘긴다.
 *
 * @param ordinal 유효 러닝 순번(1 이상, 저장 일시·기록 ID 순)
 * @param score 러닝 점수
 * @param distance 거리
 */
public record ValidRunScore(int ordinal, double score, Distance distance) {

    public ValidRunScore {
        if (ordinal < 1 || distance == null) {
            throw new IllegalArgumentException("유효 러닝 순번은 1 이상: " + ordinal);
        }
    }
}
