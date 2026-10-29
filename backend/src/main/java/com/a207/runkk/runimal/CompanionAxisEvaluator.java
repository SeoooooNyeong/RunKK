package com.a207.runkk.runimal;

import com.a207.runkk.policy.RunimalRule;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

/** 동행 축: 1 − (최근 한 달 유효 러닝 중 크루런 연결 러닝 비율)(A-14, 결정 Q-79). */
@Component
class CompanionAxisEvaluator implements AxisEvaluator {

    @Override
    public RunimalAxis axis() {
        return RunimalAxis.COMPANION;
    }

    @Override
    public OptionalDouble ratioOfA(RunSample s, RunimalRule rule) {
        if (s.lastMonth().isEmpty()) {
            return OptionalDouble.empty();
        }
        // 함께 달린 러닝 = 배틀런 또는 크루 공개 뒤의 크루런 연결 기록(v0.14 D-247). 크루 공개 전에는 RunSampleReader가 크루런을 비운다
        long together = s.lastMonth().stream().filter(RunSample.SampleRun::ranTogether).count();
        return OptionalDouble.of(1 - (double) together / s.lastMonth().size());
    }
}
