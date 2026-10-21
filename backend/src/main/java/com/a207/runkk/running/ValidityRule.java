package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.Duration;

/**
 * 유효 러닝 판정 기준(설계 6.1.3, FR-047). STANDARD는 결정 Q-22의 고정값이다.
 *
 * @param minDistance 최소 거리(1km)
 * @param minElapsed 최소 시간(3분)
 * @param fastestPace 가장 빠른 허용 평균 페이스(3:00)
 * @param slowestPace 가장 느린 허용 평균 페이스(15:00)
 * @param maxSpeedKmh 구간 최고 시속 상한(25km/h, 넘으면 무효)
 */
public record ValidityRule(
        Distance minDistance,
        Duration minElapsed,
        Pace fastestPace,
        Pace slowestPace,
        double maxSpeedKmh) {

    public static final ValidityRule STANDARD =
            new ValidityRule(
                    Distance.ofKm(1), Duration.ofMinutes(3), new Pace(180), new Pace(900), 25.0);

    /** 네 조건 모두. */
    public boolean isSatisfiedBy(Distance d, Duration t, Pace avg, double maxSegmentSpeedKmh) {
        return d.isAtLeast(minDistance)
                && t.compareTo(minElapsed) >= 0
                && avg.isWithin(fastestPace, slowestPace)
                && maxSegmentSpeedKmh <= maxSpeedKmh;
    }
}
