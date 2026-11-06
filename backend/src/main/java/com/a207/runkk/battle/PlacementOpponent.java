package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import java.time.Duration;
import java.util.Objects;

/**
 * 배치고사 가상 상대(05-21 2.2, D-480). 저장하지 않는 값이고 대결이 표·도착 시간을 남긴다(J24-13). 상대 티어는 숨기고(UNRANKED, J24-3)
 * 닉네임은 같은 거리 가상 기록에서 빌린다. 일정한 페이스다.
 */
public final class PlacementOpponent implements OpponentRecord {

    private static final String RECORD_PREFIX = "PLACEMENT-";

    private final int round;
    private final DisplayName name;
    private final StandardDistance distance;
    private final Duration finishTime;

    public PlacementOpponent(
            int round, DisplayName name, StandardDistance distance, Duration finishTime) {
        this.round = round;
        this.name = Objects.requireNonNull(name, "name");
        this.distance = Objects.requireNonNull(distance, "distance");
        if (finishTime == null || finishTime.isNegative() || finishTime.isZero()) {
            throw new IllegalArgumentException("완주 시간은 양수: " + finishTime);
        }
        this.finishTime = finishTime;
    }

    @Override
    public OpponentTrack track(Distance battleDistance) {
        return OpponentTrack.uniform(distance.distance(), finishTime).cut(battleDistance);
    }

    @Override
    public DisplayName displayName() {
        return name;
    }

    /** 상대 티어는 보이지 않는다(J24-3). */
    @Override
    public Tier displayTier() {
        return Tier.UNRANKED;
    }

    @Override
    public OpponentKind kind() {
        return OpponentKind.VIRTUAL;
    }

    @Override
    public String recordId() {
        return RECORD_PREFIX + round;
    }

    /** 균등 페이스라 가장 빠른 1km도 평균 페이스와 같다(VirtualRecord와 같은 값). */
    @Override
    public ComparisonValues comparisonValues(Distance battleDistance) {
        Pace pace = Pace.of(finishTime, distance.distance());
        return new ComparisonValues(finishTime, pace, pace, null, null);
    }

    public Duration finishTime() {
        return finishTime;
    }
}
