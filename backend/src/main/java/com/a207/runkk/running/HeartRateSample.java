package com.a207.runkk.running;

import java.time.LocalDateTime;

/**
 * 심박 측정 하나(자료사전 검증된심박값). 실패 측정은 기기에서 걸러져 온다(FR-041).
 *
 * @param measuredAt 측정 일시
 * @param bpm 분당 심박, 1~300
 */
public record HeartRateSample(LocalDateTime measuredAt, int bpm) {

    private static final int MAX_BPM = 300;

    public HeartRateSample {
        if (measuredAt == null || bpm < 1 || bpm > MAX_BPM) {
            throw new IllegalArgumentException("심박 형식 오류: " + bpm);
        }
    }
}
