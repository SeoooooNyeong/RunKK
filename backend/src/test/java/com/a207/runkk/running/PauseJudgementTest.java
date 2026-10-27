package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.METERS_PER_DEGREE;
import static com.a207.runkk.running.RunFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시험 2.1.10.1·2.1.10.2(TC-6001~TC-6025): 정지 구간을 뺀 유효 판정(D-94), 다섯째 조건 isBackedBy(+1초 경계), 정지 경계 구간
 * 제외 (PauseInterval.straddles 경계 비포함, D-163), timeToReach 정지 차감(D-122).
 */
@DisplayName("정지 구간 판정·트랙 계산(D-94, D-122, D-163)")
class PauseJudgementTest {

    private static final double V_10KMH = 5_000 / 1_800.0;

    static LocalDateTime t(int h, int m, int s) {
        return LocalDateTime.of(2026, 10, 22, h, m, s);
    }

    static LocationPoint p(LocalDateTime at, double meters) {
        return new LocationPoint(
                at, new GeoPoint(37.5 + meters / METERS_PER_DEGREE, 127.0), 5, SignalState.NORMAL);
    }

    static LocationPoint unstable(LocalDateTime at, double meters) {
        return new LocationPoint(
                at,
                new GeoPoint(37.5 + meters / METERS_PER_DEGREE, 127.0),
                5,
                SignalState.UNSTABLE);
    }

    /** from부터 to까지 1초 간격, startMeters에서 초속 mps로 북쪽으로 달린 포인트(양 끝 포함). */
    static List<LocationPoint> leg(
            LocalDateTime from, LocalDateTime to, double startMeters, double mps) {
        List<LocationPoint> points = new ArrayList<>();
        long seconds = Duration.between(from, to).toSeconds();
        for (int i = 0; i <= seconds; i++) {
            points.add(p(from.plusSeconds(i), startMeters + i * mps));
        }
        return points;
    }

    static List<LocationPoint> concat(List<LocationPoint> a, List<LocationPoint> b) {
        List<LocationPoint> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    static RunUpload free(
            String id,
            LocalDateTime start,
            LocalDateTime end,
            long meters,
            Duration elapsed,
            List<LocationPoint> points,
            List<PauseInterval> pauses) {
        return new RunUpload(
                id,
                start,
                end,
                Distance.ofMeters(meters),
                elapsed,
                RunMode.FREE,
                CardKind.FREE_RUN,
                null,
                null,
                points.size(),
                points,
                List.of(),
                List.of(),
                pauses);
    }

    static PauseInterval pause(LocalDateTime from, LocalDateTime to) {
        return new PauseInterval(from, to);
    }

    /** 07:00~07:40, 정지 [07:10, 07:20], 정지 앞뒤 시속 10km로 5km(경로 길이 5km). */
    private static List<LocationPoint> pausedTenMinutes() {
        return concat(
                leg(t(7, 0, 0), t(7, 10, 0), 0, V_10KMH),
                leg(t(7, 20, 0), t(7, 40, 0), 600 * V_10KMH, V_10KMH));
    }

    private static RunRecord pausedRun(String id, Duration elapsed, List<PauseInterval> pauses) {
        return RunRecord.receive(
                1L, free(id, t(7, 0, 0), t(7, 40, 0), 5_000, elapsed, pausedTenMinutes(), pauses));
    }

    private static RunTrack track(List<LocationPoint> points) {
        return new RunTrack(t(7, 0, 0), points, List.of(), false);
    }

    // 2.1.10.1 judgeValidity, isBackedBy

    @Test
    @DisplayName("TC-6001 J1: 두 번째 판정과 경로 파기 뒤 재판정은 IllegalStateException, 첫 판정 그대로")
    void tc6001_judgeTwice() {
        RunRecord r =
                pausedRun(id(1), Duration.ofMinutes(30), List.of(pause(t(7, 10, 0), t(7, 20, 0))));
        assertThat(r.judgeValidity(ValidityRule.STANDARD)).isTrue();
        double score = r.getScore().value();
        assertThatThrownBy(() -> r.judgeValidity(ValidityRule.STANDARD))
                .isInstanceOf(IllegalStateException.class);
        r.purgeRoute();
        assertThatThrownBy(() -> r.judgeValidity(ValidityRule.STANDARD))
                .isInstanceOf(IllegalStateException.class);
        assertThat(r.isValid()).isTrue();
        assertThat(r.getScore().value()).isEqualTo(score);
    }

    @Test
    @DisplayName("TC-6002 J2: 네 조건 실패(900m)면 무효이고 isBackedBy를 부르지 않는다(단락 평가)")
    void tc6002_fourConditionsFail_skipsBackedBy() {
        RunRecord r =
                RunRecord.receive(
                        1L,
                        free(
                                id(2),
                                t(7, 0, 0),
                                t(7, 6, 0),
                                900,
                                Duration.ofMinutes(6),
                                leg(t(7, 0, 0), t(7, 6, 0), 0, 900 / 360.0),
                                List.of()));
        ValidityRule rule = spy(ValidityRule.STANDARD);
        assertThat(r.judgeValidity(rule)).isFalse();
        assertThat(r.getScore()).isNull();
        verify(rule, never()).isBackedBy(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-6003 J3: 네 조건은 만족하나 선언 5,000m > 경로 2,400m × 2면 무효")
    void tc6003_declaredDistanceNotBacked() {
        RunRecord r =
                RunRecord.receive(
                        1L,
                        free(
                                id(3),
                                t(7, 0, 0),
                                t(7, 30, 0),
                                5_000,
                                Duration.ofMinutes(30),
                                leg(t(7, 0, 0), t(7, 30, 0), 0, 2_400 / 1_800.0),
                                List.of()));
        assertThat(r.judgeValidity(ValidityRule.STANDARD)).isFalse();
        assertThat(r.getScore()).isNull();
    }

    @Test
    @DisplayName("TC-6004 J4: 정지 10분, 운동 30:00, 경로 5km면 유효, pausedTotal 10:00")
    void tc6004_validWithPause() {
        RunRecord r =
                pausedRun(id(4), Duration.ofMinutes(30), List.of(pause(t(7, 10, 0), t(7, 20, 0))));
        assertThat(r.judgeValidity(ValidityRule.STANDARD)).isTrue();
        assertThat(r.getScore().value())
                .isEqualTo(RunningScore.of(Distance.ofKm(5), Duration.ofMinutes(30)).value());
        assertThat(r.pausedTotal()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("TC-6005: 선언 거리 ≤ 경로 × 2(같음 포함) 경곗값 4,999/5,000/5,001m")
    void tc6005_declaredToPathRatio() {
        ValidityRule rule = ValidityRule.STANDARD;
        Distance path = Distance.ofMeters(2_500);
        Duration elapsed = Duration.ofMinutes(20);
        Duration wall = Duration.ofMinutes(30);
        assertThat(rule.isBackedBy(Distance.ofMeters(4_999), path, elapsed, wall)).isTrue();
        assertThat(rule.isBackedBy(Distance.ofMeters(5_000), path, elapsed, wall)).isTrue();
        assertThat(rule.isBackedBy(Distance.ofMeters(5_001), path, elapsed, wall)).isFalse();
    }

    @Test
    @DisplayName("TC-6006: 운동 시간 ≤ wall + 1초 경곗값 30:00.999/30:01.000/30:01.001")
    void tc6006_wallClockTolerance() {
        ValidityRule rule = ValidityRule.STANDARD;
        Distance d = Distance.ofMeters(1_000);
        Duration wall = Duration.ofMinutes(30);
        Duration base = Duration.ofMinutes(30);
        assertThat(rule.isBackedBy(d, d, base.plusMillis(999), wall)).isTrue();
        assertThat(rule.isBackedBy(d, d, base.plusMillis(1_000), wall)).isTrue();
        assertThat(rule.isBackedBy(d, d, base.plusMillis(1_001), wall)).isFalse();
    }

    @Test
    @DisplayName("TC-6007: wall = 벽시계 − 정지. 30:01.000 유효, 30:01.001·35:00 무효, 정지 없으면 35:00 유효")
    void tc6007_wallMinusPauses() {
        List<PauseInterval> tenMinutes = List.of(pause(t(7, 10, 0), t(7, 20, 0)));
        Duration base = Duration.ofMinutes(30);
        assertThat(
                        pausedRun(id(71), base.plusMillis(1_000), tenMinutes)
                                .judgeValidity(ValidityRule.STANDARD))
                .isTrue();
        assertThat(
                        pausedRun(id(72), base.plusMillis(1_001), tenMinutes)
                                .judgeValidity(ValidityRule.STANDARD))
                .isFalse();
        assertThat(
                        pausedRun(id(73), Duration.ofMinutes(35), tenMinutes)
                                .judgeValidity(ValidityRule.STANDARD))
                .isFalse();
        RunRecord noPause =
                RunRecord.receive(
                        1L,
                        free(
                                id(74),
                                t(7, 0, 0),
                                t(7, 40, 0),
                                5_000,
                                Duration.ofMinutes(35),
                                leg(t(7, 0, 0), t(7, 40, 0), 0, 5_000 / 2_400.0),
                                List.of()));
        assertThat(noPause.judgeValidity(ValidityRule.STANDARD)).isTrue();
    }

    @Test
    @DisplayName("TC-6008: 포인트 2개(300m)에 42,195m 선언은 무효(다섯째 조건)")
    void tc6008_twoPointsMarathon() {
        LocalDateTime start = t(5, 0, 0);
        LocalDateTime end = t(8, 0, 0);
        RunRecord r =
                RunRecord.receive(
                        1L,
                        free(
                                id(8),
                                start,
                                end,
                                42_195,
                                Duration.ofHours(3),
                                List.of(p(start, 0), p(end, 300)),
                                List.of()));
        assertThat(r.judgeValidity(ValidityRule.STANDARD)).isFalse();
        assertThat(r.getScore()).isNull();
    }

    @Test
    @DisplayName("TC-6009: 운동 시간·평균 페이스는 정지를 뺀 값으로 판정한다")
    void tc6009_elapsedExcludesPauses() {
        LocalDateTime s = t(7, 0, 0);
        RunRecord tooShort =
                RunRecord.receive(
                        1L,
                        free(
                                id(91),
                                s,
                                s.plusSeconds(210),
                                1_000,
                                Duration.ofSeconds(150),
                                concat(
                                        leg(s, s.plusSeconds(60), 0, 1_000 / 150.0),
                                        leg(
                                                s.plusSeconds(120),
                                                s.plusSeconds(210),
                                                60 * 1_000 / 150.0,
                                                1_000 / 150.0)),
                                List.of(pause(s.plusSeconds(60), s.plusSeconds(120)))));
        assertThat(tooShort.judgeValidity(ValidityRule.STANDARD)).isFalse();

        RunRecord slowButValid =
                RunRecord.receive(
                        1L,
                        free(
                                id(92),
                                s,
                                s.plusMinutes(20),
                                1_000,
                                Duration.ofMinutes(14),
                                concat(
                                        leg(s, s.plusMinutes(5), 0, 1_000 / 840.0),
                                        leg(
                                                s.plusMinutes(11),
                                                s.plusMinutes(20),
                                                300 * 1_000 / 840.0,
                                                1_000 / 840.0)),
                                List.of(pause(s.plusMinutes(5), s.plusMinutes(11)))));
        assertThat(slowButValid.judgeValidity(ValidityRule.STANDARD)).isTrue();
    }

    @Test
    @DisplayName(
            "TC-6010: 정지 중 차로 5km 이동한 경계 쌍은 최고 시속·경로 길이에서 빠진다. 정지 목록을 비우면 그 쌍은 초과 구간으로 빠진다(FR-047"
                    + " v0.10)")
    void tc6010_boundaryPairExcluded() {
        double v = 5_000 / (599.0 + 1_199.0);
        List<LocationPoint> points =
                concat(
                        leg(t(7, 0, 0), t(7, 9, 59), 0, v),
                        leg(t(7, 20, 1), t(7, 40, 0), 599 * v + 5_000, v));
        RunRecord withPause =
                RunRecord.receive(
                        1L,
                        free(
                                id(10),
                                t(7, 0, 0),
                                t(7, 40, 0),
                                5_000,
                                Duration.ofMinutes(30),
                                points,
                                List.of(pause(t(7, 10, 0), t(7, 20, 0)))));
        assertThat(withPause.judgeValidity(ValidityRule.STANDARD)).isTrue();

        RunRecord noPause =
                RunRecord.receive(
                        1L,
                        free(
                                id(11),
                                t(7, 0, 0),
                                t(7, 40, 0),
                                5_000,
                                Duration.ofMinutes(30),
                                points,
                                List.of()));
        // v0.10: 기록 전체를 무효로 하지 않고 29.9km/h 쌍(5km, 602초)만 뺀다
        assertThat(noPause.judgeValidity(ValidityRule.STANDARD)).isTrue();
        assertThat(noPause.getExcludedDistance().meters()).isBetween(4_999L, 5_001L);
        assertThat(noPause.getExcludedElapsed()).isEqualTo(Duration.ofSeconds(602));
        assertThat(noPause.getDistance().meters()).isBetween(4_999L, 5_000L);
    }

    @Test
    @DisplayName("TC-6011: receive는 정지 구간을 시간순 읽기 전용으로 옮기고 pausedTotal은 길이 합(없으면 0)")
    void tc6011_receiveKeepsPauses() {
        List<PauseInterval> two =
                List.of(pause(t(7, 10, 0), t(7, 20, 0)), pause(t(7, 25, 0), t(7, 30, 0)));
        RunRecord r = pausedRun(id(12), Duration.ofMinutes(25), two);
        assertThat(r.pauses()).containsExactlyElementsOf(two);
        assertThatThrownBy(() -> r.pauses().add(pause(t(7, 31, 0), t(7, 32, 0))))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(r.pausedTotal()).isEqualTo(Duration.ofMinutes(15));

        RunRecord nullPauses = pausedRun(id(13), Duration.ofMinutes(30), null);
        assertThat(nullPauses.pauses()).isEmpty();
        assertThat(nullPauses.pausedTotal()).isZero();
        RunRecord empty = pausedRun(id(14), Duration.ofMinutes(30), List.of());
        assertThat(empty.pauses()).isEmpty();
        assertThat(empty.pausedTotal()).isZero();
    }

    // 2.1.10.2 RunTrack, PauseInterval

    @Test
    @DisplayName("TC-6012: 포인트 1개면 최고 시속 0, 경로 0m, 1km 도달 비어 있음")
    void tc6012_singlePoint() {
        RunTrack one = track(List.of(p(t(7, 0, 0), 0)));
        assertThat(one.maxSegmentSpeedKmh(List.of())).isZero();
        assertThat(one.pathLength(List.of())).isEqualTo(Distance.ZERO);
        assertThat(one.timeToReach(Distance.ofKm(1), List.of())).isEmpty();
    }

    @Test
    @DisplayName("TC-6013: 불안정 끝 쌍은 최고 시속에서만 빠지고 경로 길이에는 들어간다")
    void tc6013_unstablePair() {
        RunTrack tr = track(List.of(p(t(7, 0, 0), 0), unstable(t(7, 0, 10), 200)));
        assertThat(tr.maxSegmentSpeedKmh(List.of())).isZero();
        assertThat(tr.pathLength(List.of())).isEqualTo(Distance.ofMeters(200));
    }

    @Test
    @DisplayName("TC-6014: 정지에 걸친 쌍은 최고 시속·경로 길이 모두 0")
    void tc6014_straddlingPair() {
        RunTrack tr = track(List.of(p(t(7, 9, 59), 0), p(t(7, 20, 1), 3_000)));
        List<PauseInterval> pauses = List.of(pause(t(7, 10, 0), t(7, 20, 0)));
        assertThat(tr.maxSegmentSpeedKmh(pauses)).isZero();
        assertThat(tr.pathLength(pauses)).isEqualTo(Distance.ZERO);
    }

    @Test
    @DisplayName("TC-6015: 같은 일시 포인트 쌍(dt = 0)은 0으로 나누지 않고 뺀다")
    void tc6015_zeroDt() {
        RunTrack tr = track(List.of(p(t(7, 0, 0), 0), p(t(7, 0, 0), 5)));
        assertThat(tr.maxSegmentSpeedKmh(List.of())).isZero();
    }

    @Test
    @DisplayName("TC-6016: 쌍 3개 10/14/12km/h → 14km/h, 경로 360m")
    void tc6016_threePairs() {
        RunTrack tr =
                track(
                        List.of(
                                p(t(7, 0, 0), 0),
                                p(t(7, 0, 36), 100),
                                p(t(7, 1, 12), 240),
                                p(t(7, 1, 48), 360)));
        assertThat(tr.maxSegmentSpeedKmh(List.of())).isCloseTo(14.0, within(0.01));
        assertThat(tr.pathLength(List.of())).isEqualTo(Distance.ofMeters(360));
    }

    @Test
    @DisplayName("TC-6017: 인자 없는 연산은 빈 정지 목록과 같다")
    void tc6017_noArgDelegatesToEmpty() {
        RunTrack tr =
                track(List.of(p(t(7, 4, 59), 0), p(t(7, 9, 59), 1_000), p(t(7, 20, 1), 6_000)));
        List<PauseInterval> pauses = List.of(pause(t(7, 10, 0), t(7, 20, 0)));
        assertThat(tr.maxSegmentSpeedKmh()).isEqualTo(tr.maxSegmentSpeedKmh(List.of()));
        assertThat(tr.maxSegmentSpeedKmh()).isCloseTo(29.9, within(0.05));
        assertThat(tr.pathLength()).isEqualTo(Distance.ofMeters(6_000));
        assertThat(tr.maxSegmentSpeedKmh(pauses)).isCloseTo(12.0, within(0.01));
        assertThat(tr.pathLength(pauses)).isEqualTo(Distance.ofMeters(1_000));
    }

    @Test
    @DisplayName("TC-6018: straddles는 경계를 포함하지 않는다(a < to 그리고 b > from)")
    void tc6018_straddlesExclusive() {
        PauseInterval pz = pause(t(7, 10, 0), t(7, 20, 0));
        assertThat(pz.straddles(t(7, 19, 59).plusNanos(999_000_000), t(7, 20, 5))).isTrue();
        assertThat(pz.straddles(t(7, 20, 0), t(7, 20, 5))).isFalse();
        assertThat(pz.straddles(t(7, 9, 55), t(7, 10, 0).plusNanos(1_000_000))).isTrue();
        assertThat(pz.straddles(t(7, 9, 55), t(7, 10, 0))).isFalse();
    }

    @Test
    @DisplayName("TC-6019: 정지 0·1·2개 — 쌍보다 앞·뒤 정지는 무관, 걸치는 정지가 있으면 뺀다")
    void tc6019_pauseCounts() {
        RunTrack tr = track(List.of(p(t(7, 0, 0), 0), p(t(7, 0, 5), 30)));
        assertThat(tr.maxSegmentSpeedKmh(List.of())).isCloseTo(21.6, within(0.01));
        assertThat(tr.maxSegmentSpeedKmh(List.of(pause(t(6, 0, 0), t(6, 10, 0)))))
                .isCloseTo(21.6, within(0.01));
        assertThat(tr.maxSegmentSpeedKmh(List.of(pause(t(8, 0, 0), t(8, 10, 0)))))
                .isCloseTo(21.6, within(0.01));
        assertThat(
                        tr.maxSegmentSpeedKmh(
                                List.of(
                                        pause(t(6, 0, 0), t(6, 10, 0)),
                                        pause(t(7, 0, 2), t(7, 0, 3)))))
                .isZero();
    }

    @Test
    @DisplayName("TC-6020·TC-6021: 불안정 건너뜀만 있거나 900m에서 끝나면 1km 도달 비어 있음")
    void tc6020_6021_notReached() {
        RunTrack gap = track(List.of(p(t(7, 0, 0), 0), unstable(t(7, 5, 0), 1_200)));
        assertThat(gap.timeToReach(Distance.ofKm(1), List.of())).isEmpty();
        RunTrack shortRun = track(leg(t(7, 0, 0), t(7, 6, 0), 0, 900 / 360.0));
        assertThat(shortRun.timeToReach(Distance.ofKm(1), List.of())).isEmpty();
    }

    /** 07:00 시작, 시속 10km, 정지 [07:15, 07:25], 경계와 같은 시각의 포인트(07:15:00, 07:25:00). */
    private static RunUpload tc6022(String id, double jumpMeters) {
        List<LocationPoint> points =
                concat(
                        leg(t(7, 0, 0), t(7, 15, 0), 0, V_10KMH),
                        leg(t(7, 25, 0), t(7, 40, 0), 900 * V_10KMH + jumpMeters, V_10KMH));
        return free(
                id,
                t(7, 0, 0),
                t(7, 40, 0),
                5_000,
                Duration.ofMinutes(30),
                points,
                List.of(pause(t(7, 15, 0), t(7, 25, 0))));
    }

    @Test
    @DisplayName("TC-6022: 1km 6:00(정지 전), 5km 30:00(40:00 − 정지 10:00). 경계 시각 포인트는 정상 구간")
    void tc6022_timeToReachSubtractsPauses() {
        RunRecord r = RunRecord.receive(1L, tc6022(id(22), 0));
        assertThat(r.timeToReach(Distance.ofKm(1)))
                .hasValueSatisfying(d -> assertThat(d.toMillis()).isCloseTo(360_000L, within(5L)));
        assertThat(r.timeToReach(Distance.ofKm(5)))
                .hasValueSatisfying(
                        d -> assertThat(d.toMillis()).isCloseTo(1_800_000L, within(5L)));
    }

    @Test
    @DisplayName("TC-6023: 정지 중 300m 이동한 경계 쌍은 누적하지 않아 5km 도달은 그대로 30:00")
    void tc6023_movedDuringPause() {
        RunRecord r = RunRecord.receive(1L, tc6022(id(23), 300));
        assertThat(r.timeToReach(Distance.ofKm(5)))
                .hasValueSatisfying(
                        d -> assertThat(d.toMillis()).isCloseTo(1_800_000L, within(5L)));
    }

    @Test
    @DisplayName("TC-6024: 최고 기록은 정지를 뺀 시간(30:00). 정지 없는 31:00은 갱신하지 않는다(D-122)")
    void tc6024_personalBestExcludesPauses() {
        PersonalRecordBook book = PersonalRecordBook.of(1);
        RunRecord paused = RunRecord.receive(1L, tc6022(id(24), 0));
        assertThat(paused.judgeValidity(ValidityRule.STANDARD)).isTrue();
        book.apply(paused);
        assertThat(book.best(StandardDistance.KM5).orElseThrow().time())
                .hasValueSatisfying(d -> assertThat(d.toSeconds()).isCloseTo(1_800L, within(1L)));

        RunRecord slower =
                RunRecord.receive(
                        1L,
                        free(
                                id(25),
                                t(9, 0, 0),
                                t(9, 31, 0),
                                5_000,
                                Duration.ofMinutes(31),
                                leg(t(9, 0, 0), t(9, 31, 0), 0, 5_000 / 1_860.0),
                                List.of()));
        slower.judgeValidity(ValidityRule.STANDARD);
        assertThat(book.apply(slower)).noneMatch(m -> m.kind() == NewRecordMark.Kind.BASE_DISTANCE);
        assertThat(book.best(StandardDistance.KM5).orElseThrow().recordId()).isEqualTo(id(24));
    }

    @Test
    @DisplayName("TC-6025: PauseInterval은 from < to, duration = to − from")
    void tc6025_pauseInterval() {
        assertThatThrownBy(() -> pause(t(7, 10, 0), t(7, 10, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pause(t(7, 10, 0), t(7, 9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pause(null, t(7, 9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(pause(t(7, 10, 0), t(7, 20, 0)).duration()).isEqualTo(Duration.ofMinutes(10));
    }
}
