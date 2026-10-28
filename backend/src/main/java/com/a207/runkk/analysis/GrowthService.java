package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.RunQueryService;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성장 분석(설계 6.1.4 GrowthService, srs.md 6.3 3.3, FR-061~FR-063, FR-076). 주간은 최근 8주(월요일 시작), 월간은 최근
 * 6개월이다.
 */
@Service
@Transactional(readOnly = true)
public class GrowthService {

    static final int WEEKS = 8;
    static final int MONTHS = 6;

    private final RunQueryService runQueryService;

    public GrowthService(RunQueryService runQueryService) {
        this.runQueryService = runQueryService;
    }

    public GrowthView growth(long memberId, AggregationPeriod period) {
        int count = period == AggregationPeriod.WEEKLY ? WEEKS : MONTHS;
        Map<StandardDistance, GrowthView.Best> bests = new EnumMap<>(StandardDistance.class);
        for (StandardDistance distance : StandardDistance.values()) {
            if (distance.isRecordBase()) {
                runQueryService
                        .best(memberId, distance)
                        .ifPresent(best -> bests.put(distance, bestOf(best)));
            }
        }
        return new GrowthView(
                period,
                runQueryService.periodTotals(memberId, period, count).stream()
                        .map(
                                p ->
                                        new GrowthView.PeriodRow(
                                                p.from(),
                                                p.to(),
                                                p.distance(),
                                                p.count(),
                                                p.averagePace()))
                        .toList(),
                bests,
                runQueryService.longest(memberId).map(GrowthService::bestOf).orElse(null));
    }

    private static GrowthView.Best bestOf(BestEntry entry) {
        return new GrowthView.Best(
                entry.recordId(),
                entry.time().orElse(null),
                entry.distance().orElse(null),
                entry.achievedOn(),
                Boolean.TRUE.equals(entry.hasUnstable()));
    }
}
