package com.a207.runkk.running;

import java.time.LocalDateTime;

/**
 * 기기가 잰 움직임 표본 하나(2026-11-08 사용자 결정 "다 보내, 없으면 계산하고", S15P31A207-242). 속도는 기기 위치 속도 또는 워치 Health
 * Services SPEED, 케이던스는 걸음 센서·워치 STEPS_PER_MINUTE로 잰 값이다. 둘 중 하나만 있어도 된다. 범위 밖 값(ai 상한)은 저장하고
 * runbody.RunBodyRequestFactory가 거른다.
 *
 * @param measuredAt 측정 일시
 * @param speedMps 속도(m/s, 0 이상). 재지 않았으면 null
 * @param cadenceSpm 케이던스(분당 걸음 수, 0 이상). 재지 않았으면 null
 */
public record MotionSample(LocalDateTime measuredAt, Double speedMps, Double cadenceSpm) {

    public MotionSample {
        if (measuredAt == null) {
            throw new IllegalArgumentException("움직임 표본 측정 일시가 비었다");
        }
        if (speedMps == null && cadenceSpm == null) {
            throw new IllegalArgumentException("움직임 표본에 속도·케이던스가 모두 없다");
        }
        requireMeasure(speedMps, "속도");
        requireMeasure(cadenceSpm, "케이던스");
    }

    private static void requireMeasure(Double value, String name) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException(name + "는 0 이상의 유한한 값: " + value);
        }
    }
}
