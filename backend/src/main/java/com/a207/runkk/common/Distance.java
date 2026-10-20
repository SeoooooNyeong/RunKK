package com.a207.runkk.common;

import java.util.Locale;

/**
 * 거리 하나를 미터 단위로 표현한다(설계 6.1.1).
 *
 * @param meters 0 이상의 미터
 */
public record Distance(long meters) implements Comparable<Distance> {

    public static final Distance ZERO = new Distance(0);

    private static final double METERS_PER_KM = 1000.0;

    public Distance {
        if (meters < 0) {
            throw new IllegalArgumentException("거리는 0 이상이어야 한다: " + meters);
        }
    }

    public static Distance ofMeters(long m) {
        return new Distance(m);
    }

    public static Distance ofKm(double km) {
        if (km < 0) {
            throw new IllegalArgumentException("거리는 0 이상이어야 한다: " + km);
        }
        return new Distance(Math.round(km * METERS_PER_KM));
    }

    public Distance plus(Distance d) {
        return new Distance(meters + d.meters);
    }

    /** 결과가 음수면 ZERO. */
    public Distance minus(Distance d) {
        return new Distance(Math.max(0, meters - d.meters));
    }

    public boolean isAtLeast(Distance d) {
        return meters >= d.meters;
    }

    /** km 환산, 소수 둘째 자리 반올림. */
    public double km() {
        return Math.round(meters / 10.0) / 100.0;
    }

    /** 저장 값은 그대로 두고 표시만 단위 환산한다(FR-009). */
    public String format(DistanceUnit unit) {
        return String.format(Locale.ROOT, "%.2f %s", unit.convert(this), unit.symbol());
    }

    @Override
    public int compareTo(Distance other) {
        return Long.compare(meters, other.meters);
    }
}
