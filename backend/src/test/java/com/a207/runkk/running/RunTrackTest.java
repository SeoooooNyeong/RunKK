package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.METERS_PER_DEGREE;
import static com.a207.runkk.running.RunFixtures.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RunTrack(A-03): 정렬·중복 제거, 최고 시속(불안정 제외), 도달 시간 보간, 누적 거리, 파기, 압축 왕복. */
@org.junit.jupiter.api.DisplayName("위치 트랙 계산")
class RunTrackTest {

    private static LocationPoint at(int second, double meters, SignalState signal) {
        return new LocationPoint(
                START.plusSeconds(second),
                new GeoPoint(37.5 + meters / METERS_PER_DEGREE, 127.0),
                5,
                signal);
    }

    private static RunTrack track(List<LocationPoint> points, List<UnstableSegment> segments) {
        return new RunTrack(START, points, segments, false);
    }

    @Test
    void sortByTime_sortsAndDropsDuplicates() {
        RunTrack t =
                track(
                        List.of(
                                at(2, 20, SignalState.NORMAL),
                                at(0, 0, SignalState.NORMAL),
                                at(1, 10, SignalState.NORMAL),
                                at(1, 11, SignalState.NORMAL)),
                        List.of());
        t.sortByTime();
        assertThat(t.points())
                .extracting(LocationPoint::measuredAt)
                .containsExactly(START, START.plusSeconds(1), START.plusSeconds(2));
    }

    @Test
    void maxSegmentSpeed_ignoresSegmentEndingAtUnstablePoint() {
        List<LocationPoint> points = new ArrayList<>();
        points.add(at(0, 0, SignalState.NORMAL));
        points.add(at(1, 3, SignalState.NORMAL));
        points.add(at(2, 300, SignalState.UNSTABLE));
        points.add(at(3, 303, SignalState.NORMAL));
        RunTrack t = track(points, List.of());
        assertThat(t.maxSegmentSpeedKmh()).isCloseTo(10.8, within(0.01));

        RunTrack spike =
                track(
                        List.of(at(0, 0, SignalState.NORMAL), at(1, 8, SignalState.NORMAL)),
                        List.of());
        assertThat(spike.maxSegmentSpeedKmh()).isCloseTo(28.8, within(0.01));
    }

    @Test
    void timeToReach_interpolatesAndSkipsUnstableGap() {
        RunTrack t =
                track(
                        List.of(
                                at(0, 0, SignalState.NORMAL),
                                at(10, 100, SignalState.NORMAL),
                                at(20, 200, SignalState.NORMAL)),
                        List.of());
        assertThat(t.timeToReach(Distance.ofMeters(150))).contains(Duration.ofSeconds(15));
        assertThat(t.timeToReach(Distance.ofMeters(201))).isEmpty();
        assertThat(t.cumulativeDistanceAt(Duration.ofSeconds(5))).isEqualTo(Distance.ofMeters(50));
        assertThat(t.cumulativeDistanceAt(Duration.ofSeconds(60)))
                .isEqualTo(Distance.ofMeters(200));

        RunTrack gap =
                track(
                        List.of(
                                at(0, 0, SignalState.NORMAL),
                                at(10, 100, SignalState.NORMAL),
                                at(30, 300, SignalState.UNSTABLE),
                                at(40, 400, SignalState.NORMAL)),
                        List.of(
                                new UnstableSegment(
                                        START.plusSeconds(10),
                                        START.plusSeconds(30),
                                        Distance.ofMeters(200))));
        // 불안정 구간 200m는 누적에서 빠지므로 150m는 30초 뒤 구간의 중간에 닿는다
        assertThat(gap.timeToReach(Distance.ofMeters(150))).contains(Duration.ofSeconds(35));
        assertThat(gap.cumulativeDistanceAt(Duration.ofSeconds(40)))
                .isEqualTo(Distance.ofMeters(200));
        assertThat(gap.hasUnstableWithin(Distance.ZERO, Distance.ofMeters(150))).isTrue();
        assertThat(gap.hasUnstableWithin(Distance.ofMeters(150), Distance.ofMeters(200))).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("경로가 목표에 0.5m 안으로 모자라면 마지막 포인트 시각에 닿은 것으로 본다(W54)")
    void timeToReach_acceptsRoundingShortfallAtTrackEnd() {
        // 999.7m: 앱은 1,000m로 반올림해 도착 처리한다
        RunTrack nearly =
                track(
                        List.of(
                                at(0, 0, SignalState.NORMAL),
                                at(300, 999.7, SignalState.NORMAL),
                                at(301, 999.7, SignalState.NORMAL)),
                        List.of());
        assertThat(nearly.timeToReach(Distance.ofKm(1))).contains(Duration.ofSeconds(300));

        // 999.4m: 반올림해도 999m라 닿지 않았다
        RunTrack shortOf =
                track(
                        List.of(at(0, 0, SignalState.NORMAL), at(300, 999.4, SignalState.NORMAL)),
                        List.of());
        assertThat(shortOf.timeToReach(Distance.ofKm(1))).isEmpty();

        // 끝까지 가서 목표를 넘는 경로는 지금처럼 그 구간 안에서 보간한다
        RunTrack over =
                track(
                        List.of(
                                at(0, 0, SignalState.NORMAL),
                                at(300, 999.7, SignalState.NORMAL),
                                at(310, 1_009.7, SignalState.NORMAL)),
                        List.of());
        assertThat(over.timeToReach(Distance.ofKm(1))).contains(Duration.ofMillis(300_300));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("반올림 허용은 불안정 구간 건너뜀과 정지 차감을 그대로 따른다(W54)")
    void timeToReach_roundingShortfallSkipsUnstableAndSubtractsPause() {
        RunTrack gap =
                track(
                        List.of(
                                at(0, 0, SignalState.NORMAL),
                                at(100, 500, SignalState.NORMAL),
                                at(120, 600, SignalState.UNSTABLE),
                                at(220, 1_099.7, SignalState.NORMAL),
                                at(230, 1_150, SignalState.UNSTABLE)),
                        List.of());
        // 불안정으로 들어가는 쌍 150m를 빼면 999.7m, 마지막으로 거리가 늘어난 220초에 닿는다
        assertThat(gap.timeToReach(Distance.ofKm(1))).contains(Duration.ofSeconds(220));

        RunTrack paused =
                track(
                        List.of(
                                at(0, 0, SignalState.NORMAL),
                                at(100, 500, SignalState.NORMAL),
                                at(120, 500, SignalState.NORMAL),
                                at(220, 999.7, SignalState.NORMAL)),
                        List.of());
        PauseInterval pause = new PauseInterval(START.plusSeconds(100), START.plusSeconds(120));
        // 정지 20초를 뺀 운동 시간 200초
        assertThat(paused.timeToReach(Distance.ofKm(1), List.of(pause)))
                .contains(Duration.ofSeconds(200));
    }

    @Test
    void curve_sweepMatchesCumulativeDistance() {
        RunTrack t =
                new RunTrack(START, RunFixtures.straightLine(START, 3.0, 100), List.of(), false);
        List<CurvePoint> curve = t.curve(Duration.ofSeconds(1), Duration.ofMillis(10_500));
        assertThat(curve).hasSize(12);
        assertThat(curve.get(0)).isEqualTo(new CurvePoint(Duration.ZERO, Distance.ZERO));
        assertThat(curve.get(5).distance())
                .isEqualTo(t.cumulativeDistanceAt(Duration.ofSeconds(5)));
        assertThat(curve.get(11).elapsed()).isEqualTo(Duration.ofMillis(10_500));
        assertThat(curve.get(11).distance().meters()).isBetween(31L, 32L);
    }

    @Test
    void purge_dropsPointsKeepsSegments() {
        UnstableSegment seg =
                new UnstableSegment(START, START.plusSeconds(1), Distance.ofMeters(5));
        RunTrack t =
                track(
                        List.of(at(0, 0, SignalState.NORMAL), at(1, 5, SignalState.NORMAL)),
                        List.of(seg));
        assertThat(t.firstPoint()).isPresent();
        t.purge();
        assertThat(t.isPurged()).isTrue();
        assertThat(t.points()).isEmpty();
        assertThat(t.firstPoint()).isEmpty();
        assertThat(t.timeToReach(Distance.ofMeters(1))).isEmpty();
        assertThat(t.unstableSegments()).containsExactly(seg);
        assertThat(t.hasUnstableWithin(Distance.ZERO, Distance.ofKm(1))).isTrue();
    }

    @Test
    void encodePoints_roundTrips() {
        List<LocationPoint> points = RunFixtures.straightLine(START, 3.0, 30);
        RunTrack t = new RunTrack(START, points, List.of(), false);
        assertThat(RunTrack.decodePoints(START, t.encodePoints()))
                .hasSize(31)
                .allSatisfy(p -> assertThat(p.signal()).isEqualTo(SignalState.NORMAL));
        assertThat(RunTrack.decodePoints(START, t.encodePoints()).get(30).point().latitude())
                .isCloseTo(points.get(30).point().latitude(), within(1e-9));
        assertThat(RunTrack.decodePoints(START, null)).isEmpty();
    }
}
