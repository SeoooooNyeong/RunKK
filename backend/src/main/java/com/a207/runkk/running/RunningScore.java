package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.Duration;
import java.util.List;

/**
 * 러닝 한 회의 점수(Daniels–Gilbert VDOT, 설계 6.2 A-01, D-12). 가상 기록 페이스는 같은 식을 이분 탐색으로 거꾸로 푼다.
 *
 * @param value VDOT
 */
public record RunningScore(double value) {

    private static final long MIN_METERS = 1_000;
    private static final Duration MIN_ELAPSED = Duration.ofMinutes(3);
    private static final double SEARCH_LOW_MINUTES = 2;
    private static final double SEARCH_HIGH_MINUTES = 600;
    private static final int SEARCH_STEPS = 50;
    private static final double MILLIS_PER_MINUTE = 60_000.0;

    /** d ≥ 1km, t ≥ 3분일 때 VDOT. 예: 5km 25:00 → 약 38.3. */
    public static RunningScore of(Distance d, Duration t) {
        if (d.meters() < MIN_METERS || t.compareTo(MIN_ELAPSED) < 0) {
            throw new IllegalArgumentException("점수는 1km·3분 이상에서만 계산한다");
        }
        return new RunningScore(vdot(d.meters(), t.toMillis() / MILLIS_PER_MINUTE));
    }

    /** 평균. 빈 목록이면 IllegalArgumentException. */
    public static RunningScore average(List<RunningScore> scores) {
        if (scores == null || scores.isEmpty()) {
            throw new IllegalArgumentException("점수가 없다");
        }
        return new RunningScore(
                scores.stream().mapToDouble(RunningScore::value).average().orElseThrow());
    }

    /** 이 점수로 d를 달리는 평균 페이스(이분 탐색 50회, 시간이 길수록 점수가 작아진다). */
    public Pace paceFor(Distance d) {
        double lo = SEARCH_LOW_MINUTES;
        double hi = SEARCH_HIGH_MINUTES;
        for (int i = 0; i < SEARCH_STEPS; i++) {
            double mid = (lo + hi) / 2;
            if (vdot(d.meters(), mid) > value) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return Pace.of(Duration.ofMillis(Math.round(hi * MILLIS_PER_MINUTE)), d);
    }

    private static double vdot(double meters, double minutes) {
        double v = meters / minutes;
        double vo2 = -4.60 + 0.182258 * v + 0.000104 * v * v;
        double pctMax =
                0.8
                        + 0.1894393 * Math.exp(-0.012778 * minutes)
                        + 0.2989558 * Math.exp(-0.1932605 * minutes);
        return vo2 / pctMax;
    }
}
