package com.a207.runkk.running;

import java.util.List;

/**
 * 러닝 기록 확정 뒤 늦게 온 심박(FR-043, POST /runs/{id}/heart-rates).
 *
 * @param recordId 기록 ID
 * @param samples 심박 측정값
 */
public record LateHeartRateCommand(String recordId, List<HeartRateSample> samples) {

    public LateHeartRateCommand {
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("기록 ID가 비었다");
        }
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
