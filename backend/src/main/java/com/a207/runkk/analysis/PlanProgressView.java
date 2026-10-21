package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.LocalDate;
import java.util.List;

/**
 * 계획 대비 실제(설계 6.1.4 CoachService.progress, FR-074, FR-187).
 *
 * @param days 기간 안 날짜별 줄(오름차순)
 */
public record PlanProgressView(List<Day> days) {

    /**
     * 하루.
     *
     * @param date 날짜
     * @param planned 계획 거리, 계획이 없으면 null
     * @param targetPace 목표 페이스, 없으면 null
     * @param done 실제 유효 거리
     * @param actualPace 실제 평균 페이스, 기록이 없으면 null
     * @param shortfall 부족 거리, 계획이 없으면 null
     * @param supplement 보충 추천 거리(오늘 줄만), 없으면 null
     */
    public record Day(
            LocalDate date,
            Distance planned,
            Pace targetPace,
            Distance done,
            Pace actualPace,
            Distance shortfall,
            Distance supplement) {}
}
