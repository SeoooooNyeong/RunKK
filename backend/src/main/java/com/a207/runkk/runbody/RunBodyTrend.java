package com.a207.runkk.runbody;

import com.a207.runkk.running.ValidRunStat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 런바디 요약 "최근 10회 변화"(G-14). 유효 러닝을 순번대로 10회씩 묶은 회차(1~10번째가 1회차, 11~20번째가 2회차)에서 마지막으로 다 찬 회차(최근
 * 10회)를 바로 앞 회차(직전 10회)와 비교한다. Runimal이 10회마다 유형을 확정하는 회차(FR-153)와 같은 묶음이라 "나의 러닝 유형"과 같은 10회를 본다.
 * 피그마 G-14의 "2회차, 11~20번째 러닝"이 이 묶음이다. ai 월간 기준선(baseline, 최근 최대 10회 평균)은 직전 10회 값이 없어 쓰지 않는다.
 *
 * <ul>
 *   <li>평균 페이스: 거리로 가중한 평균(각 러닝 평균 페이스 × 거리의 합 ÷ 거리 합) = 10회 전체 시간 ÷ 전체 거리
 *   <li>빈도: 10회를 그 회차에서 가장 이른 러닝 날부터 가장 늦은 러닝 날까지의 날 수로 나눈다. 주간은 × 7(날 수가 7보다 작으면 7로 본다), 월간은 ×
 *       30(날 수가 30보다 작으면 30으로 본다). 소수 첫째 자리로 반올림
 *   <li>같은 심박 페이스: 속도가 심박에 비례한다고 보고(속도 ÷ 심박이 효율) 최근 10회 페이스를 직전 10회 평균 심박으로 환산한 값과 직전 10회 페이스의
 *       차이(초/km, 양수면 같은 심박에서 빨라졌다). 각 회차에서 심박이 있는 러닝만 쓰고 그런 러닝이 3회(ai 기준선과 같은 최소)보다 적으면 셈하지 않는다
 * </ul>
 *
 * @param validRunCount 유효 러닝 수
 * @param round 최근 10회의 회차(0이면 아직 10회가 안 됐다)
 * @param needs 1회차까지 남은 러닝 수(round가 0일 때만 0보다 크다)
 * @param recent 최근 10회. round가 0이면 null. 수를 센 뒤 그 회차 기록이 지워져 셈할 러닝이 없어도 null(MR !91 BR-3)
 * @param previous 직전 10회. round가 2 미만이면 null. 그 회차 기록이 지워져 셈할 러닝이 없어도 null
 * @param sameHeartRatePaceChange 같은 심박 페이스 변화(초/km, 양수면 빨라짐). 셈할 수 없으면 null
 */
public record RunBodyTrend(
        int validRunCount,
        int round,
        int needs,
        Window recent,
        Window previous,
        Integer sameHeartRatePaceChange) {

    /** 한 회차의 러닝 수. */
    public static final int WINDOW = 10;

    /** 심박 평균·같은 심박 페이스를 셈하는 데 필요한 심박 있는 러닝 수. */
    static final int MIN_HEART_RATE_RUNS = 3;

    private static final int WEEK_DAYS = 7;
    private static final int MONTH_DAYS = 30;

    /**
     * 한 회차(10회)의 값.
     *
     * @param fromOrdinal 첫 유효 러닝 순번
     * @param toOrdinal 마지막 유효 러닝 순번
     * @param paceSecondsPerKm 평균 페이스(초/km, 거리 가중)
     * @param runsPerWeek 주간 러닝 빈도(회/주)
     * @param runsPerMonth 월간 러닝 빈도(회/30일)
     * @param averageHeartRate 심박 있는 러닝의 시간 가중 평균 심박. 그런 러닝이 3회보다 적으면 null
     */
    public record Window(
            int fromOrdinal,
            int toOrdinal,
            int paceSecondsPerKm,
            double runsPerWeek,
            double runsPerMonth,
            Integer averageHeartRate) {}

    /** 회차 수. */
    public static int roundOf(int validRunCount) {
        return Math.max(0, validRunCount) / WINDOW;
    }

    /**
     * 회차 목록은 수를 센 뒤 따로 읽으므로 그 사이 기록이 지워지면 비거나 짧을 수 있다. 남은 러닝으로 셈하고, 셈할 러닝이 없으면 그 회차를 null로 둔다(예외로
     * 400을 내지 않는다, MR !91 BR-3).
     *
     * @param validRunCount 유효 러닝 수
     * @param recentRuns 최근 10회(roundOf가 0이면 빈 목록)
     * @param previousRuns 직전 10회(roundOf가 2 미만이면 빈 목록)
     */
    public static RunBodyTrend of(
            int validRunCount, List<ValidRunStat> recentRuns, List<ValidRunStat> previousRuns) {
        int round = roundOf(validRunCount);
        if (round == 0) {
            return new RunBodyTrend(
                    validRunCount, 0, WINDOW - Math.max(0, validRunCount), null, null, null);
        }
        Window recent = window(recentRuns);
        Window previous = round >= 2 ? window(previousRuns) : null;
        return new RunBodyTrend(
                validRunCount,
                round,
                0,
                recent,
                previous,
                recent == null || previous == null
                        ? null
                        : sameHeartRateChange(recentRuns, previousRuns));
    }

    /** 한 회차 값. 러닝이 없거나 거리 합이 0이면 null. */
    private static Window window(List<ValidRunStat> runs) {
        if (runs.isEmpty() || runs.stream().mapToLong(r -> r.distance().meters()).sum() <= 0) {
            return null;
        }
        ValidRunStat first = runs.get(0);
        ValidRunStat last = runs.get(runs.size() - 1);
        LocalDate earliest =
                runs.stream().map(r -> r.startedAt().toLocalDate()).min(LocalDate::compareTo).get();
        LocalDate latest =
                runs.stream().map(r -> r.startedAt().toLocalDate()).max(LocalDate::compareTo).get();
        long days = ChronoUnit.DAYS.between(earliest, latest) + 1;
        return new Window(
                first.ordinal(),
                last.ordinal(),
                (int) Math.round(pace(runs)),
                perUnit(runs.size(), days, WEEK_DAYS),
                perUnit(runs.size(), days, MONTH_DAYS),
                heartRate(runs));
    }

    private static double perUnit(int runs, long days, int unitDays) {
        double perUnit = runs * (double) unitDays / Math.max(days, unitDays);
        return Math.round(perUnit * 10) / 10.0;
    }

    /** 거리 가중 평균 페이스(초/km). */
    private static double pace(List<ValidRunStat> runs) {
        double seconds = 0;
        long meters = 0;
        for (ValidRunStat run : runs) {
            seconds += movingSeconds(run);
            meters += run.distance().meters();
        }
        if (meters == 0) {
            throw new IllegalArgumentException("거리 합이 0이다");
        }
        return seconds * 1000 / meters;
    }

    private static double movingSeconds(ValidRunStat run) {
        return run.averagePace().secondsPerKm() * run.distance().meters() / 1000.0;
    }

    private static List<ValidRunStat> withHeartRate(List<ValidRunStat> runs) {
        return runs.stream()
                .filter(r -> r.averageHeartRate() != null && r.averageHeartRate() > 0)
                .filter(r -> r.distance().meters() > 0)
                .toList();
    }

    /** 심박 있는 러닝의 시간 가중 평균 심박. 3회보다 적으면 null. */
    private static Integer heartRate(List<ValidRunStat> runs) {
        Double hr = heartRateOrNull(withHeartRate(runs));
        return hr == null ? null : (int) Math.round(hr);
    }

    private static Double heartRateOrNull(List<ValidRunStat> hrRuns) {
        if (hrRuns.size() < MIN_HEART_RATE_RUNS) {
            return null;
        }
        double weighted = 0;
        double seconds = 0;
        for (ValidRunStat run : hrRuns) {
            double t = movingSeconds(run);
            weighted += run.averageHeartRate() * t;
            seconds += t;
        }
        return seconds == 0 ? null : weighted / seconds;
    }

    private static Integer sameHeartRateChange(
            List<ValidRunStat> recentRuns, List<ValidRunStat> previousRuns) {
        List<ValidRunStat> recent = withHeartRate(recentRuns);
        List<ValidRunStat> previous = withHeartRate(previousRuns);
        Double recentHr = heartRateOrNull(recent);
        Double previousHr = heartRateOrNull(previous);
        if (recentHr == null || previousHr == null) {
            return null;
        }
        double recentAtPreviousHr = pace(recent) * recentHr / previousHr;
        return (int) Math.round(pace(previous) - recentAtPreviousHr);
    }
}
