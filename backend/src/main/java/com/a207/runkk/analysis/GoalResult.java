package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.LocalDate;

/**
 * 목표·계획 저장 결과(설계 6.1.4 CoachService.setWeeklyGoal·putPlan).
 *
 * @param date 주간 목표면 주의 월요일, 계획이면 그 날짜
 * @param distance 목표·계획 거리
 * @param count 주간 목표 횟수, 계획이면 null
 * @param targetPace 계획 목표 페이스, 없으면 null
 */
public record GoalResult(LocalDate date, Distance distance, Integer count, Pace targetPace) {

    static GoalResult of(WeeklyGoal goal) {
        return new GoalResult(goal.week().monday(), goal.distance(), goal.count(), null);
    }

    static GoalResult of(DailyPlan plan) {
        return new GoalResult(plan.date(), plan.distance(), null, plan.targetPace());
    }
}
