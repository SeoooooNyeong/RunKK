package com.a207.runkk.policy;

import java.util.OptionalInt;

/**
 * 호흡 안내 기준 심박 규칙(설계 6.1.9).
 *
 * @param ratio 최대 심박 대비 비율
 * @param defaultBpm 나이를 모를 때 기준 심박
 * @param holdSeconds 기준을 넘긴 채 유지된 시간
 * @param cooldownSeconds 안내 사이 간격
 */
public record CoachingRule(double ratio, int defaultBpm, int holdSeconds, int cooldownSeconds) {

    private static final int MAX_HEART_RATE_BASE = 220;

    /** (220 − 나이) × ratio, 나이가 없으면 defaultBpm. */
    public int thresholdFor(OptionalInt age) {
        if (age.isEmpty()) {
            return defaultBpm;
        }
        return (int) Math.round((MAX_HEART_RATE_BASE - age.getAsInt()) * ratio);
    }
}
