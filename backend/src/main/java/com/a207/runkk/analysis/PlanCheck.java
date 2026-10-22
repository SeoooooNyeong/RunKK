package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;

/**
 * 계획 하루의 이행 점검(설계 6.1.4 PlanCheck, FR-074).
 *
 * @param plan 계획
 * @param actual 그날 실제 유효 거리
 * @param actualPace 그날 실제 평균 페이스, 기록이 없으면 null
 * @param shortfall 부족 거리 = max(0, 계획 − 실제)
 * @param fulfilled 부족이 없는가
 */
public record PlanCheck(
        DailyPlan plan, Distance actual, Pace actualPace, Distance shortfall, boolean fulfilled) {}
