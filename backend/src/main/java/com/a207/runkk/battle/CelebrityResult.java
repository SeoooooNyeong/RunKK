package com.a207.runkk.battle;

/**
 * 유명인 기록 관리 결과(자료사전 유명인관리결과, design-review.md M31). 거부는 BusinessRuleException 사유 코드로 알린다.
 *
 * @param celebrityId 유명인 기록 ID
 * @param outcome 처리 결과
 */
public record CelebrityResult(long celebrityId, Outcome outcome) {

    /** 처리 결과. */
    public enum Outcome {
        REGISTERED,
        DELETED
    }
}
