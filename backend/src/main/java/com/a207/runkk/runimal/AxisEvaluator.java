package com.a207.runkk.runimal;

import com.a207.runkk.policy.RunimalRule;
import java.util.OptionalDouble;

/** 축 하나의 A쪽 비율 계산(설계 6.1.8, 6.3 인터페이스 추출). 구현 4개가 축마다 다른 비율 계산을 맡는다(6.4). */
interface AxisEvaluator {

    RunimalAxis axis();

    /** A쪽 비율. 판정할 표본이 없으면 비어 있다. */
    OptionalDouble ratioOfA(RunSample s, RunimalRule rule);

    /**
     * 표본이 없을 때의 쪽. 기본 A(부록 E E-08). CourseAxisEvaluator만 B(탐험길)로 재정의한다(FR-152·FR-166 보완, D-147).
     */
    default AxisLean leanWithoutSample() {
        return AxisLean.A;
    }

    /** 기준과 비교(A-14). */
    default AxisLean lean(double ratioOfA, RunimalRule rule) {
        return axis().lean(ratioOfA, rule);
    }
}
