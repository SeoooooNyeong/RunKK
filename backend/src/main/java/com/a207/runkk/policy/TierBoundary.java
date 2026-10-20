package com.a207.runkk.policy;

import com.a207.runkk.common.Tier;
import java.util.List;

/**
 * 러닝 점수를 티어로 바꾸는 경계(설계 6.1.9). 하한 4개: 실버·골드·플래티넘·마스터(기본 30·35·40·45).
 *
 * @param lowerBounds 오름차순 하한 4개
 */
public record TierBoundary(List<Double> lowerBounds) {

    private static final List<Tier> RANKS_ABOVE_BRONZE =
            List.of(Tier.SILVER, Tier.GOLD, Tier.PLATINUM, Tier.MASTER);

    public TierBoundary {
        if (lowerBounds == null || lowerBounds.size() != RANKS_ABOVE_BRONZE.size()) {
            throw new IllegalArgumentException("티어 경계는 4개다");
        }
        for (int i = 1; i < lowerBounds.size(); i++) {
            if (lowerBounds.get(i) <= lowerBounds.get(i - 1)) {
                throw new IllegalArgumentException("티어 경계는 오름차순이다");
            }
        }
        lowerBounds = List.copyOf(lowerBounds);
    }

    /** 점수가 속한 랭크 티어(첫 경계 미만은 브론즈). */
    public Tier tierOf(double score) {
        Tier tier = Tier.BRONZE;
        for (int i = 0; i < lowerBounds.size(); i++) {
            if (score >= lowerBounds.get(i)) {
                tier = RANKS_ABOVE_BRONZE.get(i);
            }
        }
        return tier;
    }

    /** 티어 구간의 가운데 점수. 열린 구간(브론즈 아래, 마스터 위)은 가상 기록 규칙의 점수로 닫는다. */
    public double midScoreOf(Tier t, VirtualRecordRule r) {
        if (!t.isRanked()) {
            throw new IllegalArgumentException("랭크 티어가 아니다: " + t);
        }
        int index = RANKS_ABOVE_BRONZE.indexOf(t);
        double lower = index < 0 ? r.openLowScore() : lowerBounds.get(index);
        double upper =
                index + 1 < lowerBounds.size() ? lowerBounds.get(index + 1) : r.openHighScore();
        return (lower + upper) / 2;
    }
}
