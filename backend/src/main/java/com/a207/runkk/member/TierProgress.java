package com.a207.runkk.member;

import com.a207.runkk.common.Distance;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 티어 평가에 쓸 유효 러닝 누계를 보관한다(설계 6.1.2, D-27). 평가 시점은 running이 넘긴 유효 러닝 순번으로 정하고 validRunCount는 직전 반영
 * 순번이다(D-37). 최근 점수는 RunningScore 대신 값(double)으로 둔다(member는 running에 의존하지 않는다, 12.2). 첫 배치(유효 3회째)는
 * 배치고사로 옮겨 이 창이 없고 재평가(10의 배수)만 남는다(v0.30 J24-6, D-484). 재평가를 실제로 반영할지는 Member가 정한다(랭크 티어, 확정 판 순번
 * 뒤).
 */
@Embeddable
public class TierProgress {

    private static final Logger log = LoggerFactory.getLogger(TierProgress.class);
    private static final int WINDOW = 10;

    @Column(name = "valid_run_count", nullable = false)
    private int validRunCount;

    /** 최근 10개 점수(오래된 것부터, 쉼표 구분). */
    @Column(name = "recent_scores", nullable = false, length = 200)
    private String recentScores;

    @Column(name = "total_valid_meters", nullable = false)
    private long totalValidMeters;

    protected TierProgress() {}

    private TierProgress(int validRunCount, String recentScores, long totalValidMeters) {
        this.validRunCount = validRunCount;
        this.recentScores = recentScores;
        this.totalValidMeters = totalValidMeters;
    }

    static TierProgress empty() {
        return new TierProgress(0, "", 0);
    }

    /**
     * 누계를 갱신한다. 이미 반영한 순번이면 무시하고 비어 있음. 재평가 지점(10의 배수)을 지났으면 최근 10회 평균을 돌려준다(FR-027). 1 넘게 건너뛰면 경고
     * 로그를 남기되 그 사이 판정 지점은 놓치지 않는다(D-40). 반영 여부는 Member가 정한다(D-484).
     */
    public OptionalDouble addValidRun(RunScoreInput input) {
        int ordinal = input.validRunOrdinal();
        if (ordinal <= validRunCount) {
            return OptionalDouble.empty();
        }
        if (ordinal - validRunCount > 1) {
            log.warn("tier_progress_ordinal_skipped last={} received={}", validRunCount, ordinal);
        }
        Deque<Double> scores = scores();
        scores.addLast(input.score());
        while (scores.size() > WINDOW) {
            scores.removeFirst();
        }
        this.recentScores =
                scores.stream()
                        .map(s -> String.format(Locale.ROOT, "%.4f", s))
                        .collect(Collectors.joining(","));
        this.totalValidMeters += input.distance().meters();
        boolean reevaluation = reevaluationPointBetween(validRunCount, ordinal).isPresent();
        this.validRunCount = ordinal;
        if (!reevaluation) {
            return OptionalDouble.empty();
        }
        return scores.stream()
                .skip(Math.max(0, scores.size() - WINDOW))
                .mapToDouble(Double::doubleValue)
                .average();
    }

    /**
     * lastOrdinal &lt; p ≤ newOrdinal인 10의 배수 p 가운데 가장 큰 것(재평가 지점, D-37, D-40, D-484). 없으면 비어 있다.
     */
    public static OptionalInt reevaluationPointBetween(int lastOrdinal, int newOrdinal) {
        int largestTen = (newOrdinal / WINDOW) * WINDOW;
        if (largestTen > lastOrdinal && largestTen >= WINDOW) {
            return OptionalInt.of(largestTen);
        }
        return OptionalInt.empty();
    }

    /**
     * lastOrdinal에서 newOrdinal로 넘어갈 때 일어날 수 있는 평가(v0.14 D-294). 재평가(그 10의 배수 회차)만 낸다. 첫
     * 배치(FIRST_PLACEMENT)는 배치고사로 옮겨 새로 만들지 않는다(옛 기록 표시용 값만 남음, D-484).
     */
    public static Optional<TierEvaluation> evaluationBetween(int lastOrdinal, int newOrdinal) {
        OptionalInt point = reevaluationPointBetween(lastOrdinal, newOrdinal);
        if (point.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TierEvaluation(EvaluationKind.REEVALUATION, point.getAsInt()));
    }

    /**
     * 늦게 온 유효 러닝(총괄 결정, 설계 쟁점 7-3). 뒤 순번이 먼저 반영되어 이미 지난 순번이다. 판정하지 않고 거리를 더하며, 최근 10회 창 안의
     * 순번이면(ordinal &gt; validRunCount − 10) 점수도 창에 넣고 가장 오래된 점수를 밀어낸다(창의 평균만 쓰므로 순서는 상관없다). 이중 반영은
     * running의 기록별 반영 표시가 막는다. 아직 오지 않은 순번이면 IllegalArgumentException.
     */
    public void addLateValidRun(RunScoreInput input) {
        int ordinal = input.validRunOrdinal();
        if (ordinal > validRunCount) {
            throw new IllegalArgumentException("늦은 순번이 아니다: " + ordinal);
        }
        if (ordinal > validRunCount - WINDOW) {
            Deque<Double> scores = scores();
            scores.addLast(input.score());
            while (scores.size() > WINDOW) {
                scores.removeFirst();
            }
            this.recentScores =
                    scores.stream()
                            .map(s -> String.format(Locale.ROOT, "%.4f", s))
                            .collect(Collectors.joining(","));
        }
        this.totalValidMeters += input.distance().meters();
    }

    /** 누적 유효 거리(레벨 계산용). */
    public Distance totalValidDistance() {
        return Distance.ofMeters(totalValidMeters);
    }

    int validRunCount() {
        return validRunCount;
    }

    private Deque<Double> scores() {
        Deque<Double> scores = new ArrayDeque<>();
        if (recentScores != null && !recentScores.isEmpty()) {
            Arrays.stream(recentScores.split(",")).map(Double::valueOf).forEach(scores::addLast);
        }
        return scores;
    }
}
