package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 성장 분석(설계 6.1.4 GrowthService.growth, srs.md 6.3 3.3, FR-061~FR-063, FR-076).
 *
 * @param period 집계 기간(주간 8주, 월간 6개월)
 * @param periods 기간별 거리·횟수·평균 페이스(오래된 것부터)
 * @param bests 기준 거리별 최고 기록(기록이 있는 거리만)
 * @param longest 최장 거리 기록, 없으면 null
 */
public record GrowthView(
        AggregationPeriod period,
        List<PeriodRow> periods,
        Map<StandardDistance, Best> bests,
        Best longest) {

    /**
     * 한 기간.
     *
     * @param from 시작일
     * @param to 종료일
     * @param distance 거리 합
     * @param count 횟수
     * @param averagePace 평균 페이스, 기록이 없으면 null
     */
    public record PeriodRow(
            LocalDate from, LocalDate to, Distance distance, int count, Pace averagePace) {}

    /**
     * 최고 기록 하나.
     *
     * @param recordId 기록 ID
     * @param time 기준 거리 완주 시간(최장 기록이면 null)
     * @param distance 최장 거리(기준 거리 기록이면 null)
     * @param achievedOn 달성일
     * @param hasUnstable 그 구간에 불안정 구간이 있었나(FR-063 표시)
     */
    public record Best(
            String recordId,
            Duration time,
            Distance distance,
            LocalDate achievedOn,
            boolean hasUnstable) {}
}
