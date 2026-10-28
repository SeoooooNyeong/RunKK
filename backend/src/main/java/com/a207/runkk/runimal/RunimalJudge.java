package com.a207.runkk.runimal;

import com.a207.runkk.policy.RunimalRule;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

/**
 * 표본으로 Runimal 유형을 판정한다(설계 6.1.8, A-14, FR-152, FR-153). 축 평가기 4개가 비율을 계산한다. v0.11에서 러너 성향 태그를
 * 지워(FR-119, D-91) 임시·확정 판정 모두 4축 데이터만 쓴다. 표본이 없는 축은 비율 0.5(카드 표시용)와 그 평가기의 leanWithoutSample()(코스는
 * 탐험길, 나머지는 A)로 둔다(D-147).
 */
@Component
class RunimalJudge {

    private static final double UNDECIDED = 0.5;

    private final List<AxisEvaluator> evaluators;

    RunimalJudge(List<AxisEvaluator> evaluators) {
        if (evaluators.size() != RunimalAxis.values().length) {
            throw new IllegalArgumentException("축 평가기는 4개다: " + evaluators.size());
        }
        this.evaluators = List.copyOf(evaluators);
    }

    /** 3번째 유효 러닝의 임시 유형(FR-152). 4축 모두 데이터, 표본 없는 축은 leanWithoutSample(). */
    Judged judgeProvisional(RunSample s, RunimalRule rule) {
        return judge(s, rule);
    }

    /** 10의 배수 유효 러닝의 확정 유형(FR-153). 4축 모두 데이터, 표본 없는 축은 leanWithoutSample()(A-14, FR-166). */
    Judged judgeConfirmed(RunSample s, RunimalRule rule) {
        return judge(s, rule);
    }

    private Judged judge(RunSample s, RunimalRule rule) {
        Map<RunimalAxis, Double> ratios = new EnumMap<>(RunimalAxis.class);
        Map<RunimalAxis, AxisLean> leans = new EnumMap<>(RunimalAxis.class);
        for (AxisEvaluator evaluator : evaluators) {
            RunimalAxis axis = evaluator.axis();
            OptionalDouble data = evaluator.ratioOfA(s, rule);
            if (data.isPresent()) {
                ratios.put(axis, data.getAsDouble());
                leans.put(axis, evaluator.lean(data.getAsDouble(), rule));
            } else {
                ratios.put(axis, UNDECIDED);
                leans.put(axis, evaluator.leanWithoutSample());
            }
        }
        return new Judged(RunimalType.of(leans), new AxisRatios(ratios));
    }
}
