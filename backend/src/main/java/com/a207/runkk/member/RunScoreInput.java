package com.a207.runkk.member;

import com.a207.runkk.common.Distance;

/**
 * 티어 갱신에 넘길 유효 러닝 한 회의 값(설계 6.1.2). running.TierUpdateListener가 넘긴다.
 *
 * @param score 러닝 점수(VDOT)
 * @param distance 거리
 * @param validRunOrdinal 그 회원의 유효 러닝 순번, 1 이상(D-37)
 */
public record RunScoreInput(double score, Distance distance, int validRunOrdinal) {

    public RunScoreInput {
        if (distance == null || validRunOrdinal < 1) {
            throw new IllegalArgumentException("유효 러닝 순번은 1 이상: " + validRunOrdinal);
        }
    }
}
