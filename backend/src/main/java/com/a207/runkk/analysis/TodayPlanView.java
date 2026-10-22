package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.LocalDate;

/**
 * 오늘 계획(설계 6.1.4 CoachService.todayPlan, FR-042, FR-048, FR-187). 워치 시작 화면·코치 계획 따르기용.
 *
 * @param date 오늘
 * @param distance 계획 거리, 계획이 없으면 null
 * @param targetPace 목표 페이스, 없으면 null
 * @param supplement 보충 추천 거리(계획이 없으면 0)
 */
public record TodayPlanView(
        LocalDate date, Distance distance, Pace targetPace, Distance supplement) {

    public boolean hasPlan() {
        return distance != null;
    }
}
