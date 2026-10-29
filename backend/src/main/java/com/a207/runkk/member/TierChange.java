package com.a207.runkk.member;

import com.a207.runkk.common.Tier;
import java.util.Optional;

/**
 * 유효 러닝 한 회를 반영한 뒤의 티어·레벨(설계 6.1.2 recordValidRun).
 *
 * @param previousTier 반영 전 티어
 * @param tier 반영 뒤 티어
 * @param previousLevel 반영 전 레벨
 * @param level 반영 뒤 레벨
 * @param evaluated 이 반영이 일으킨 평가(없으면 null, v0.14 D-291)
 */
public record TierChange(
        Tier previousTier, Tier tier, int previousLevel, int level, TierEvaluation evaluated) {

    /** 평가 없는 반영. */
    public TierChange(Tier previousTier, Tier tier, int previousLevel, int level) {
        this(previousTier, tier, previousLevel, level, null);
    }

    /** 이 반영이 평가를 일으켰으면 그 평가(첫 배치·재평가, D-294). */
    public Optional<TierEvaluation> evaluation() {
        return Optional.ofNullable(evaluated);
    }

    public boolean tierChanged() {
        return previousTier != tier;
    }
}
