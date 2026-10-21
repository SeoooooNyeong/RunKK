package com.a207.runkk.runimal;

import com.a207.runkk.policy.RunimalRule;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

/**
 * 습관 축: 최근 10회 중 시작 시각이 원형 중앙값 ±habitWindow(2시간) 안인 비율(A-14). 23시와 1시가 2시간 차이가 되도록 24시간 원 위에서 계산한다.
 */
@Component
class HabitAxisEvaluator implements AxisEvaluator {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final int MINUTES_PER_HOUR = 60;

    @Override
    public RunimalAxis axis() {
        return RunimalAxis.HABIT;
    }

    @Override
    public OptionalDouble ratioOfA(RunSample s, RunimalRule rule) {
        if (s.recent().isEmpty()) {
            return OptionalDouble.empty();
        }
        List<Integer> starts =
                s.recent().stream()
                        .map(
                                r ->
                                        r.startedAt().getHour() * MINUTES_PER_HOUR
                                                + r.startedAt().getMinute())
                        .toList();
        int median = circularMedian(starts);
        long window = rule.habitWindow().toMinutes();
        long inWindow = starts.stream().filter(m -> circularGap(m, median) <= window).count();
        return OptionalDouble.of((double) inWindow / starts.size());
    }

    /** 원 위 거리 합이 가장 작은 표본 시각(표본 10개라 O(n²)). */
    static int circularMedian(List<Integer> minutes) {
        int best = minutes.get(0);
        long bestCost = Long.MAX_VALUE;
        for (int candidate : minutes) {
            long cost = minutes.stream().mapToLong(m -> circularGap(m, candidate)).sum();
            if (cost < bestCost) {
                bestCost = cost;
                best = candidate;
            }
        }
        return best;
    }

    /** 24시간 원 위의 분 차이(0~720). */
    static int circularGap(int a, int b) {
        int gap = Math.abs(a - b) % MINUTES_PER_DAY;
        return Math.min(gap, MINUTES_PER_DAY - gap);
    }
}
