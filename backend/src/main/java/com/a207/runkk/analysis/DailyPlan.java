package com.a207.runkk.analysis;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import java.time.LocalDate;

/**
 * 날짜별 러닝 계획(설계 6.1.4 DailyPlan, FR-073).
 *
 * @param date 날짜
 * @param distance 계획 거리(0 초과)
 * @param targetPace 목표 페이스, 없으면 null
 */
public record DailyPlan(LocalDate date, Distance distance, Pace targetPace) {

    public DailyPlan {
        if (date == null || distance == null || distance.meters() <= 0) {
            throw new BusinessRuleException(ReasonCode.PLAN_INVALID);
        }
    }
}
