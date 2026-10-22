package com.a207.runkk.member;

import com.a207.runkk.common.Tier;

/**
 * 유효 러닝 한 회를 반영한 뒤의 티어·레벨(설계 6.1.2 recordValidRun).
 *
 * @param previousTier 반영 전 티어
 * @param tier 반영 뒤 티어
 * @param previousLevel 반영 전 레벨
 * @param level 반영 뒤 레벨
 */
public record TierChange(Tier previousTier, Tier tier, int previousLevel, int level) {

    public boolean tierChanged() {
        return previousTier != tier;
    }
}
