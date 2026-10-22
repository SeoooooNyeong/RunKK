package com.a207.runkk.runimal;

import com.a207.runkk.policy.RunimalRule;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

/** 코스 축: 최근 10회 중 같은 코스로 판정된 비율. 판정이 없는 기록(경로 파기·비교 대상 없음)은 뺀다. 표본이 없으면 비어 있다(A-14). */
@Component
class CourseAxisEvaluator implements AxisEvaluator {

    @Override
    public RunimalAxis axis() {
        return RunimalAxis.COURSE;
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
