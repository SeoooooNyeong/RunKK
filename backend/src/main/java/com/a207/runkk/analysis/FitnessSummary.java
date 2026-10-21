package com.a207.runkk.analysis;

import com.a207.runkk.running.RunSummary;
import com.a207.runkk.running.RunningScore;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 최근 유효 러닝 10회의 러닝 체력 요약(설계 6.1.4 FitnessSummary, FR-064, srs.md 6.3 3.4.1). RunSummary의
 * 거리·시간·점수·시작일시 4항목만 쓴다. 부호 있는 거리 변화는 미터 정수로 둔다(설계 "표현은 meters long").
 *
 * @param fromRun 요약 첫 유효 러닝 순번
 * @param toRun 요약 마지막 유효 러닝 순번
 * @param averageScore 최근 평균 러닝 점수(VDOT)
 * @param distanceChangeMeters 직전 10회 대비 1회 평균 거리 변화(미터, 부호 있음)
 * @param paceChangeSeconds 직전 10회 대비 평균 페이스 변화(km당 초, 음수면 빨라짐)
 * @param frequentDay 자주 달린 요일
 * @param frequentHourBand 자주 달린 시간대(3시간 단위 0~7)
 * @param frequencyChange 직전 10회 대비 주당 러닝 횟수 변화(빈도 규칙 문구용, 설계 공백 보충)
 */
public record FitnessSummary(
        int fromRun,
        int toRun,
        RunningScore averageScore,
        long distanceChangeMeters,
        int paceChangeSeconds,
        DayOfWeek frequentDay,
        int frequentHourBand,
        double frequencyChange) {

    /** 요약에 쓰는 최근 유효 러닝 수(FR-064). */
    public static final int WINDOW = 10;

    static final int HOURS_PER_BAND = 3;
    private static final double DAYS_PER_WEEK = 7.0;

    public FitnessSummary {
        if (fromRun < 1 || toRun < fromRun || toRun - fromRun >= WINDOW) {
            throw new IllegalArgumentException("요약 순번 오류: " + fromRun + "~" + toRun);
        }
        Objects.requireNonNull(averageScore, "평균 점수");
        Objects.requireNonNull(frequentDay, "자주 달린 요일");
    }

    /**
     * recent = 최근 유효 10회(최신순, 10회 미만이면 있는 만큼), previous = 그 직전 10회(최신순). 직전이 없으면 변화량 0. 순번은 1부터
     * 매기고, 실제 순번은 {@link #numbered(int)}로 맞춘다.
     */
    public static FitnessSummary from(List<RunSummary> recent, List<RunSummary> previous) {
        if (recent.isEmpty() || recent.size() > WINDOW) {
            throw new IllegalArgumentException("최근 유효 러닝은 1~10회: " + recent.size());
        }
        List<RunningScore> scores =
                recent.stream()
                        .map(RunSummary::score)
                        .filter(Objects::nonNull)
                        .map(RunningScore::new)
                        .toList();
        boolean compared = !previous.isEmpty();
        return new FitnessSummary(
                1,
                recent.size(),
                RunningScore.average(scores),
                compared ? averageMeters(recent) - averageMeters(previous) : 0,
                compared ? paceSeconds(recent) - paceSeconds(previous) : 0,
                mostFrequent(recent, run -> run.startedAt().getDayOfWeek()),
                mostFrequent(recent, run -> run.startedAt().getHour() / HOURS_PER_BAND),
                compared ? runsPerWeek(recent) - runsPerWeek(previous) : 0);
    }

    /** 마지막 순번을 toRun으로 맞춘 요약(running이 정한 유효 러닝 순번, D-37). */
    public FitnessSummary numbered(int toRun) {
        int count = runCount();
        return new FitnessSummary(
                toRun - count + 1,
                toRun,
                averageScore,
                distanceChangeMeters,
                paceChangeSeconds,
                frequentDay,
                frequentHourBand,
                frequencyChange);
    }

    /** 평균 점수 값(VDOT). running 형식을 모르는 쪽(infra.integration.ai SummaryIn)이 쓴다(12.2 D60). */
    public double averageScoreValue() {
        return averageScore.value();
    }

    /** 요약에 쓴 러닝 수(1~10). ai SummaryIn.runCount. */
    public int runCount() {
        return toRun - fromRun + 1;
    }

    public Trend distanceTrend() {
        return Trend.ofDistance(distanceChangeMeters);
    }

    public Trend paceTrend() {
        return Trend.ofPace(paceChangeSeconds);
    }

    public Trend frequencyTrend() {
        return Trend.ofFrequency(frequencyChange);
    }

    private static long averageMeters(List<RunSummary> runs) {
        return Math.round(runs.stream().mapToLong(r -> r.distance().meters()).average().orElse(0));
    }

    /** 거리 가중 평균 페이스(km당 초). */
    private static int paceSeconds(List<RunSummary> runs) {
        long meters = runs.stream().mapToLong(r -> r.distance().meters()).sum();
        long millis = runs.stream().map(RunSummary::elapsed).mapToLong(Duration::toMillis).sum();
        return meters == 0 ? 0 : (int) Math.round((double) millis / meters);
    }

    /** 첫 기록부터 마지막 기록까지(최소 1주)의 주당 횟수. */
    private static double runsPerWeek(List<RunSummary> runs) {
        LocalDateTime first =
                runs.stream()
                        .map(RunSummary::startedAt)
                        .min(Comparator.naturalOrder())
                        .orElseThrow();
        LocalDateTime last =
                runs.stream()
                        .map(RunSummary::startedAt)
                        .max(Comparator.naturalOrder())
                        .orElseThrow();
        double weeks = Math.max(1.0, (ChronoUnit.DAYS.between(first, last) + 1) / DAYS_PER_WEEK);
        return runs.size() / weeks;
    }

    /** 가장 많이 나온 값. 같으면 더 최근 러닝의 값(recent는 최신순). */
    private static <T> T mostFrequent(List<RunSummary> recent, Function<RunSummary, T> key) {
        Map<T, Integer> counts = new LinkedHashMap<>();
        recent.forEach(run -> counts.merge(key.apply(run), 1, Integer::sum));
        T best = null;
        int bestCount = 0;
        for (Map.Entry<T, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }
}
