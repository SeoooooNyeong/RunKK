package com.a207.runkk.common;

import java.time.Duration;
import java.util.Locale;

/**
 * 1km를 달리는 데 걸린 시간을 표현한다(설계 6.1.1).
 *
 * @param secondsPerKm 0보다 큰 초
 */
public record Pace(int secondsPerKm) {

    private static final double SECONDS_PER_HOUR = 3600.0;

    public Pace {
        if (secondsPerKm <= 0) {
            throw new IllegalArgumentException("페이스는 0보다 커야 한다: " + secondsPerKm);
        }
    }

    /** 경과 시간 ÷ 거리. 거리가 0이면 IllegalArgumentException. */
    public static Pace of(Duration elapsed, Distance distance) {
        if (distance.meters() == 0) {
            throw new IllegalArgumentException("거리가 0이면 페이스를 계산할 수 없다");
        }
        double seconds = elapsed.toMillis() / 1000.0;
        return new Pace((int) Math.round(seconds * 1000.0 / distance.meters()));
    }

    /** 목표보다 ratio 이상 느린가: secondsPerKm ≥ target × (1 + ratio). */
    public boolean isSlowerThanBy(Pace target, double ratio) {
        if (ratio < 0) {
            throw new IllegalArgumentException("ratio는 0 이상이어야 한다: " + ratio);
        }
        return secondsPerKm >= target.secondsPerKm * (1 + ratio);
    }

    /** fastest ≤ this ≤ slowest (FR-047). */
    public boolean isWithin(Pace fastest, Pace slowest) {
        return secondsPerKm >= fastest.secondsPerKm && secondsPerKm <= slowest.secondsPerKm;
    }

    public double speedKmh() {
        return SECONDS_PER_HOUR / secondsPerKm;
    }

    /** "5'30\"" 형식. mi 단위면 1마일당 시간으로 바꿔 보인다. */
    public String format(DistanceUnit unit) {
        long seconds = Math.round(secondsPerKm * unit.metersPerUnit() / 1000.0);
        return String.format(Locale.ROOT, "%d'%02d\"", seconds / 60, seconds % 60);
    }
}
