package com.a207.runkk.runimal;

import com.a207.runkk.policy.RunimalRule;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

/**
 * 코스 축: 최근 10회 중 같은 코스로 판정된 비율. 판정이 없는 기록(경로 파기)은 빼고, 비교 대상이 없던 러닝은 CourseSimilarityService가 같은 코스
 * 아님으로 저장해 표본에 든다(FR-166). 표본이 없으면 비어 있고 그때의 쪽은 탐험길(B)이다(D-147).
 */
@Component
class CourseAxisEvaluator implements AxisEvaluator {

    @Override
    public RunimalAxis axis() {
        return RunimalAxis.COURSE;
    }

    /** 코스 표본이 없으면 탐험길(B, FR-152·FR-166 보완, D-147). */
    @Override
    public AxisLean leanWithoutSample() {
        return AxisLean.B;
    }

    @Override
    public OptionalDouble ratioOfA(RunSample s, RunimalRule rule) {
        List<Boolean> judged =
                s.recent().stream()
                        .map(RunSample.SampleRun::sameCourse)
                        .filter(verdict -> verdict != null)
                        .toList();
        if (judged.isEmpty()) {
            return OptionalDouble.empty();
        }
        long same = judged.stream().filter(Boolean::booleanValue).count();
        return OptionalDouble.of((double) same / judged.size());
    }
}
