package com.a207.runkk.analysis;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.Week;

/**
 * 한 주의 러닝 목표(설계 6.1.4 WeeklyGoal, FR-072).
 *
 * @param week 주
 * @param count 목표 횟수(1~7)
 * @param distance 목표 거리(0 초과)
 */
public record WeeklyGoal(Week week, int count, Distance distance) {

    static final int MIN_COUNT = 1;
    static final int MAX_COUNT = 7;
    private static final int PERCENT = 100;

    public WeeklyGoal {
        if (week == null
                || count < MIN_COUNT
                || count > MAX_COUNT
                || distance == null
                || distance.meters() <= 0) {
            throw new BusinessRuleException(ReasonCode.GOAL_INVALID);
        }
    }

    /** 범위를 어기면 BusinessRuleException(GOAL_INVALID). */
    public static WeeklyGoal of(Week week, int count, Distance distance) {
        return new WeeklyGoal(week, count, distance);
    }

    /** 달성률. 100%를 넘어도 실제 값 그대로(FR-056). */
    public WeeklyAchievement achievement(int actualCount, Distance actualDistance) {
        return new WeeklyAchievement(
                this,
                actualCount,
                actualDistance,
                actualCount * PERCENT / count,
                (int) (actualDistance.meters() * PERCENT / distance.meters()));
    }
}
