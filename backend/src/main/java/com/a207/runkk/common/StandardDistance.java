package com.a207.runkk.common;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** 기준 거리(설계 6.1.1). 쓰임새는 이름 있는 형식(Use)의 집합으로 적어 생성자 인자가 위치로만 읽히지 않게 한다(1단계 리뷰 M1). */
public enum StandardDistance {
    KM1(1_000, Use.RANDOM_BATTLE, Use.CREW_RUN, Use.RECORD_BASE),
    KM3(3_000, Use.RANDOM_BATTLE, Use.SELF_BATTLE, Use.CREW_RUN, Use.RECORD_BASE),
    KM5(5_000, Use.RANDOM_BATTLE, Use.SELF_BATTLE, Use.CREW_RUN, Use.RECORD_BASE, Use.CELEBRITY),
    KM10(10_000, Use.RANDOM_BATTLE, Use.SELF_BATTLE, Use.CREW_RUN, Use.RECORD_BASE, Use.CELEBRITY),
    HALF(21_097, Use.SELF_BATTLE, Use.RECORD_BASE, Use.CELEBRITY),
    FULL(42_195, Use.CELEBRITY);

    /** 기준 거리를 쓰는 곳. */
    private enum Use {
        RANDOM_BATTLE,
        SELF_BATTLE,
        CREW_RUN,
        RECORD_BASE,
        CELEBRITY
    }

    private final Distance distance;
    private final Set<Use> uses;

    StandardDistance(long meters, Use... uses) {
        this.distance = Distance.ofMeters(meters);
        this.uses = uses.length == 0 ? Set.of() : Set.copyOf(EnumSet.copyOf(Arrays.asList(uses)));
    }

    public Distance distance() {
        return distance;
    }

    /** 1·3·5·10km. */
    public boolean isRandomBattle() {
        return uses.contains(Use.RANDOM_BATTLE);
    }

    /** 3·5·10km·하프. */
    public boolean isSelfBattle() {
        return uses.contains(Use.SELF_BATTLE);
    }

    /** 1·3·5·10km. */
    public boolean isCrewRun() {
        return uses.contains(Use.CREW_RUN);
    }

    /** 1·3·5·10km·하프. */
    public boolean isRecordBase() {
        return uses.contains(Use.RECORD_BASE);
    }

    /** 5·10km·하프·풀(유명인 기록 거리, 배틀런 QA 결정). */
    public boolean isCelebrity() {
        return uses.contains(Use.CELEBRITY);
    }

    /** d 이하인 가장 긴 기준 거리. */
    public static Optional<StandardDistance> nearestAtMost(Distance d) {
        return Arrays.stream(values())
                .filter(s -> d.isAtLeast(s.distance))
                .max(Comparator.comparing(StandardDistance::distance));
    }
}
