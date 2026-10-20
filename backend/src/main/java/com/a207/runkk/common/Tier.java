package com.a207.runkk.common;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 티어(설계 6.1.1). UNRANKED는 유효 러닝 3회 전이다. */
public enum Tier {
    UNRANKED,
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
    MASTER;

    public boolean isRanked() {
        return this != UNRANKED;
    }

    /** 위아래 한 단계 랭크 티어. UNRANKED는 빈 집합. */
    public Set<Tier> adjacent() {
        if (!isRanked()) {
            return Set.of();
        }
        return ranked().stream()
                .filter(t -> Math.abs(t.ordinal() - ordinal()) == 1)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 같은 티어 → 인접 → 전체 랭크 티어(FR-095). UNRANKED는 전체 랭크 티어 하나. */
    public List<Set<Tier>> searchLadder() {
        if (!isRanked()) {
            return List.of(ranked());
        }
        return List.of(Set.of(this), adjacent(), ranked());
    }

    private static Set<Tier> ranked() {
        return Arrays.stream(values())
                .filter(Tier::isRanked)
                .collect(Collectors.toUnmodifiableSet());
    }
}
