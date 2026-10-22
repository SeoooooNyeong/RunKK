package com.a207.runkk.running;

import java.util.Optional;

/** 심박 구간 5개(자료사전 심박구간, FR-185): 최대 심박 대비 50~60/60~70/70~80/80~90/90%+. 50% 미만은 세지 않는다. */
public enum HeartRateZone {
    Z1(0.5),
    Z2(0.6),
    Z3(0.7),
    Z4(0.8),
    Z5(0.9);

    private final double lowerRatio;

    HeartRateZone(double lowerRatio) {
        this.lowerRatio = lowerRatio;
    }

    /** bpm이 속한 구간. 최대 심박의 50% 미만이면 비어 있음. */
    public static Optional<HeartRateZone> of(int bpm, int maxHeartRate) {
        double ratio = (double) bpm / maxHeartRate;
        HeartRateZone found = null;
        for (HeartRateZone zone : values()) {
            if (ratio >= zone.lowerRatio) {
                found = zone;
            }
        }
        return Optional.ofNullable(found);
    }
}
