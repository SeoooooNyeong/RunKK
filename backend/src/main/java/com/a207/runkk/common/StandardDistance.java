package com.a207.runkk.common;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/** 기준 거리(설계 6.1.1). */
public enum StandardDistance {
    KM1(1_000, true, false, true, true),
    KM3(3_000, true, true, true, true),
    KM5(5_000, true, true, true, true),
    KM10(10_000, true, true, true, true),
    HALF(21_097, false, true, false, true),
    FULL(42_195, false, false, false, false);

    private final Distance distance;
    private final boolean randomBattle;
    private final boolean selfBattle;
    private final boolean crewRun;
    private final boolean recordBase;

    StandardDistance(
            long meters,
            boolean randomBattle,
            boolean selfBattle,
            boolean crewRun,
            boolean recordBase) {
        this.distance = Distance.ofMeters(meters);
        this.randomBattle = randomBattle;
        this.selfBattle = selfBattle;
        this.crewRun = crewRun;
        this.recordBase = recordBase;
    }

    public Distance distance() {
        return distance;
    }

    /** 1·3·5·10km. */
    public boolean isRandomBattle() {
        return randomBattle;
    }

    /** 3·5·10km·하프. */
    public boolean isSelfBattle() {
        return selfBattle;
    }

    /** 1·3·5·10km. */
    public boolean isCrewRun() {
        return crewRun;
    }

    /** 1·3·5·10km·하프. */
    public boolean isRecordBase() {
        return recordBase;
    }

    /** d 이하인 가장 긴 기준 거리. */
    public static Optional<StandardDistance> nearestAtMost(Distance d) {
        return Arrays.stream(values())
                .filter(s -> d.isAtLeast(s.distance))
                .max(Comparator.comparing(StandardDistance::distance));
    }
}
