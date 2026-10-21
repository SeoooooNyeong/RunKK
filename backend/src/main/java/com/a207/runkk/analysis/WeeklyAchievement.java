package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;

/**
 * 주간 목표 달성률(설계 6.1.4 WeeklyGoal.achievement, FR-056).
 *
 * @param goal 목표
 * @param actualCount 실제 유효 러닝 횟수
 * @param actualDistance 실제 유효 거리
 * @param countPercent 횟수 달성률(%), 100을 넘을 수 있다
 * @param distancePercent 거리 달성률(%), 100을 넘을 수 있다
 */
public record WeeklyAchievement(
        WeeklyGoal goal,
        int actualCount,
        Distance actualDistance,
        int countPercent,
        int distancePercent) {}
