package com.a207.runkk.runimal;

import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.policy.RunimalRule;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 표본과 성향 태그로 Runimal 유형을 판정한다(설계 6.1.8, A-14, FR-152, FR-153). 축 평가기 4개가 비율을 계산하고 이 클래스는 축마다 데이터·태그
 * 중 무엇을 쓸지만 정한다.
 */
@Component
class RunimalJudge {

    /** 임시 유형에서 태그를 먼저 보는 축(3회로는 판정하기 어렵다, W-14). */
    private static final Set<RunimalAxis> TAG_FIRST_AXES =
            Set.of(RunimalAxis.COMPANION, RunimalAxis.COURSE);

    private static final double SIDE_A = 1.0;
    private static final double SIDE_B = 0.0;
    private static final double UNDECIDED = 0.5;

    private final List<AxisEvaluator> evaluators;

    RunimalJudge(List<AxisEvaluator> evaluators) {
        if (evaluators.size() != RunimalAxis.values().length) {
            throw new IllegalArgumentException("축 평가기는 4개다: " + evaluators.size());
        }
        this.evaluators = List.copyOf(evaluators);
    }

    /** 거리·습관은 데이터, 동행·코스는 태그(1·3분류) 값. 태그가 없으면 데이터(제안, W-14). 데이터도 없으면 A로 둔다(제안, 부록 E E-08). */
    Judged judgeProvisional(RunSample s, TagSet tags, RunimalRule rule) {
        Map<RunimalAxis, Double> ratios = new EnumMap<>(RunimalAxis.class);
        for (AxisEvaluator evaluator : evaluators) {
            RunimalAxis axis = evaluator.axis();
            Optional<Double> tagged =
                    TAG_FIRST_AXES.contains(axis) ? tagRatio(axis, tags) : Optional.empty();
            ratios.put(axis, tagged.orElseGet(() -> orElse(evaluator.ratioOfA(s, rule), SIDE_A)));
        }
        return judged(ratios, rule);
    }

    /** 4축 모두 데이터. 코스 표본이 없으면 태그의 코스 분류 값, 그 밖에 표본이 없으면 0.5(A-14, FR-166). */
    Judged judgeConfirmed(RunSample s, TagSet tags, RunimalRule rule) {
        Map<RunimalAxis, Double> ratios = new EnumMap<>(RunimalAxis.class);
        for (AxisEvaluator evaluator : evaluators) {
            OptionalDouble data = evaluator.ratioOfA(s, rule);
            double fallback =
                    evaluator.axis() == RunimalAxis.COURSE
                            ? tagRatio(RunimalAxis.COURSE, tags).orElse(UNDECIDED)
                            : UNDECIDED;
            ratios.put(evaluator.axis(), orElse(data, fallback));
        }
        return judged(ratios, rule);
    }

    private Judged judged(Map<RunimalAxis, Double> ratios, RunimalRule rule) {
        Map<RunimalAxis, AxisLean> leans = new EnumMap<>(RunimalAxis.class);
        for (AxisEvaluator evaluator : evaluators) {
            leans.put(evaluator.axis(), evaluator.lean(ratios.get(evaluator.axis()), rule));
        }
        return new Judged(RunimalType.of(leans), new AxisRatios(ratios));
    }

    /** 그 축 분류의 태그가 있으면 A쪽이면 1, B쪽이면 0. */
    private static Optional<Double> tagRatio(RunimalAxis axis, TagSet tags) {
        Optional<PersonalityTag> tag = tags.valueOf(axis.category());
        return tag.map(t -> axis.isSideA(t) ? SIDE_A : SIDE_B);
    }

    private static double orElse(OptionalDouble value, double fallback) {
        return value.isPresent() ? value.getAsDouble() : fallback;
    }
}
