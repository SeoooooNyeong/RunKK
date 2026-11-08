package com.a207.runkk.runbody;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.running.ValidRunStat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 런바디 요약 G-14 "최근 10회 변화" 셈 규칙. */
@DisplayName("최근 10회 변화는 10회 회차끼리 페이스·같은 심박 페이스·빈도를 비교한다")
class RunBodyTrendTest {

    private static final LocalDateTime D0 = LocalDateTime.of(2026, 9, 1, 7, 0);

    /** firstOrdinal부터 10회, everyDays일 간격, 5km, 같은 페이스·심박. */
    private static List<ValidRunStat> window(
            int firstOrdinal, int startDay, int everyDays, int pace, Integer heartRate) {
        List<ValidRunStat> runs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            runs.add(
                    new ValidRunStat(
                            firstOrdinal + i,
                            D0.plusDays(startDay + (long) i * everyDays),
                            Distance.ofMeters(5_000),
                            new Pace(pace),
                            heartRate));
        }
        return runs;
    }

    @Test
    void beforeTenRuns_noWindow_needsTheRest() {
        RunBodyTrend trend = RunBodyTrend.of(7, List.of(), List.of());
        assertThat(trend.round()).isZero();
        assertThat(trend.needs()).isEqualTo(3);
        assertThat(trend.recent()).isNull();
        assertThat(trend.previous()).isNull();
        assertThat(trend.sameHeartRatePaceChange()).isNull();
    }

    @Test
    @DisplayName("BR-3: 수를 센 뒤 회차 기록이 지워져 회차 목록이 비면 예외(400) 대신 그 회차를 null로 둔다")
    void windowEmptiedByDeletion_isNullNotError() {
        RunBodyTrend recentGone = RunBodyTrend.of(20, List.of(), window(1, 0, 3, 362, 150));
        assertThat(recentGone.round()).isEqualTo(2);
        assertThat(recentGone.recent()).isNull();
        assertThat(recentGone.previous().fromOrdinal()).isEqualTo(1);
        assertThat(recentGone.sameHeartRatePaceChange()).isNull();

        RunBodyTrend bothGone = RunBodyTrend.of(20, List.of(), List.of());
        assertThat(bothGone.recent()).isNull();
        assertThat(bothGone.previous()).isNull();

        RunBodyTrend previousGone = RunBodyTrend.of(20, window(11, 30, 2, 348, 150), List.of());
        assertThat(previousGone.recent().toOrdinal()).isEqualTo(20);
        assertThat(previousGone.previous()).isNull();
    }

    @Test
    void firstRound_hasRecentOnly() {
        RunBodyTrend trend = RunBodyTrend.of(15, window(1, 0, 3, 362, 150), List.of());
        assertThat(trend.round()).isEqualTo(1);
        assertThat(trend.needs()).isZero();
        assertThat(trend.recent().fromOrdinal()).isEqualTo(1);
        assertThat(trend.recent().toOrdinal()).isEqualTo(10);
        assertThat(trend.previous()).isNull();
        assertThat(trend.sameHeartRatePaceChange()).isNull();
    }

    @Test
    void secondRound_comparesPaceFrequencyAndSameHeartRatePace() {
        // 직전 10회: 3일 간격(28일) 6'02", 심박 149. 최근 10회: 2일 간격(19일) 5'48", 심박 150
        RunBodyTrend trend =
                RunBodyTrend.of(23, window(11, 40, 2, 348, 150), window(1, 0, 3, 362, 149));
        assertThat(trend.round()).isEqualTo(2);
        assertThat(trend.recent().paceSecondsPerKm()).isEqualTo(348);
        assertThat(trend.previous().paceSecondsPerKm()).isEqualTo(362);
        assertThat(trend.previous().runsPerWeek()).as("10 × 7 / 28 = 2.5").isEqualTo(2.5);
        assertThat(trend.recent().runsPerWeek()).as("10 × 7 / 19 = 3.68").isEqualTo(3.7);
        assertThat(trend.previous().runsPerMonth())
                .as("10 × 30 / 30(28일은 30일로 본다)")
                .isEqualTo(10.0);
        assertThat(trend.recent().averageHeartRate()).isEqualTo(150);
        // 348 × 150 / 149 = 350.3 → 362 − 350.3 = 11.7 → 12초 빨라짐
        assertThat(trend.sameHeartRatePaceChange()).isEqualTo(12);
    }

    @Test
    void windowPace_isDistanceWeighted_andShortSpanUsesUnitDays() {
        List<ValidRunStat> runs = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            runs.add(new ValidRunStat(i + 1, D0, Distance.ofMeters(1_000), new Pace(400), null));
        }
        runs.add(new ValidRunStat(10, D0, Distance.ofMeters(9_000), new Pace(300), null));
        RunBodyTrend trend = RunBodyTrend.of(10, runs, List.of());
        assertThat(trend.recent().paceSecondsPerKm())
                .as("(9 × 400 + 9 × 300) / 18 = 350")
                .isEqualTo(350);
        assertThat(trend.recent().runsPerWeek()).as("하루에 10회여도 7일로 본다").isEqualTo(10.0);
        assertThat(trend.recent().averageHeartRate()).isNull();
    }

    @Test
    void sameHeartRatePace_needsThreeRunsWithHeartRateInEachWindow() {
        List<ValidRunStat> recent = window(11, 40, 2, 348, null);
        recent.set(0, withHeartRate(recent.get(0), 150));
        recent.set(1, withHeartRate(recent.get(1), 150));
        RunBodyTrend trend = RunBodyTrend.of(20, recent, window(1, 0, 3, 362, 149));
        assertThat(trend.sameHeartRatePaceChange()).isNull();
        assertThat(trend.recent().averageHeartRate()).isNull();

        recent.set(2, withHeartRate(recent.get(2), 150));
        assertThat(RunBodyTrend.of(20, recent, window(1, 0, 3, 362, 149)).sameHeartRatePaceChange())
                .isEqualTo(12);
    }

    @Test
    void lateUploadedRun_spanUsesEarliestAndLatestDate() {
        List<ValidRunStat> runs = window(1, 10, 1, 360, null);
        ValidRunStat last = runs.get(9);
        runs.set(
                9,
                new ValidRunStat(
                        10, D0, last.distance(), last.averagePace(), last.averageHeartRate()));
        // 시작 날짜: 0일, 10~18일 → 19일
        assertThat(RunBodyTrend.of(10, runs, List.of()).recent().runsPerWeek()).isEqualTo(3.7);
    }

    private static ValidRunStat withHeartRate(ValidRunStat r, int hr) {
        return new ValidRunStat(r.ordinal(), r.startedAt(), r.distance(), r.averagePace(), hr);
    }
}
