package com.a207.runkk.runimal;

import com.a207.runkk.policy.RunimalRule;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

/** 거리 축: 최근 10회 중 기준 거리(3km) 미만 비율(A-14). */
@Component
class DistanceAxisEvaluator implements AxisEvaluator {

    @Override
    public RunimalAxis axis() {
        return RunimalAxis.DISTANCE;
    }

    @Override
    public OptionalDouble ratioOfA(RunSample s, RunimalRule rule) {
        if (s.recent().isEmpty()) {
            return OptionalDouble.empty();
        }
        long shorter =
                s.recent().stream()
                        .filter(r -> r.distance().compareTo(rule.shortDistance()) < 0)
                        .count();
        return OptionalDouble.of((double) shorter / s.recent().size());
    }
}
