package com.a207.runkk.running;

import static com.a207.runkk.running.OverspeedFixtures.carRide;
import static com.a207.runkk.running.OverspeedFixtures.pathMeters;
import static com.a207.runkk.running.OverspeedFixtures.shortJump;
import static com.a207.runkk.running.OverspeedFixtures.teleportOnly;
import static com.a207.runkk.running.OverspeedFixtures.upload;
import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-047 v0.10(사용자 결정): 이웃 포인트 사이 시속 25km/h를 넘는 구간은 기록 전체를 무효로 하지 않고 그 구간의 거리·시간만 뺀 뒤 남은 값으로
 * 1km·3분·3:00~15:00을 판정한다. ValidityRule·RunTrack·RunRecord.
 */
@DisplayName("초과 시속 구간 제외 판정(FR-047 v0.10)")
class OverspeedJudgementTest {

    private static RunRecord judged(RunUpload u) {
        RunRecord r = RunRecord.receive(1L, u);
        r.judgeValidity(ValidityRule.STANDARD);
        return r;
    }

    @Test
    @DisplayName("RunTrack: 25km/h 넘는 쌍만 고르고, 불안정 끝 쌍·정지 경계 쌍은 보지 않는다")
    void track_overspeedSegments() {
        List<LocationPoint> points = shortJump(START);
        RunTrack track = new RunTrack(START, points, List.of(), false);
        ExcludedSegments ex = track.overspeedSegments(List.of(), ValidityRule.STANDARD);
        assertThat(ex.intervals())
                .containsExactly(new PauseInterval(START.plusSeconds(450), START.plusSeconds(451)));
        assertThat(ex.distance()).isEqualTo(Distance.ofMeters(50));
        assertThat(ex.elapsed()).isEqualTo(Duration.ofSeconds(1));
        assertThat(track.countedLength(ex.withPauses(List.of())))
                .isEqualTo(Distance.ofMeters(3_090));
        assertThat(track.pathLength()).isEqualTo(Distance.ofMeters(3_140));

        // 정지에 걸친 쌍은 초과 구간이 아니다(정지 경계 쌍, D-163)
        List<PauseInterval> pause =
                List.of(
                        new PauseInterval(
                                START.plusSeconds(450).plusNanos(100_000_000),
                                START.plusSeconds(450).plusNanos(900_000_000)));
        assertThat(track.overspeedSegments(pause, ValidityRule.STANDARD).isEmpty()).isTrue();

        // 불안정 포인트로 끝나는 쌍도 보지 않는다(A-03)
        List<LocationPoint> unstable =
                points.stream()
                        .map(
                                p ->
                                        p.measuredAt().equals(START.plusSeconds(451))
                                                ? new LocationPoint(
                                                        p.measuredAt(),
                                                        p.point(),
                                                        p.accuracyMeters(),
                                                        SignalState.UNSTABLE)
                                                : p)
                        .toList();
        assertThat(
                        new RunTrack(START, unstable, List.of(), false)
                                .overspeedSegments(List.of(), ValidityRule.STANDARD))
                .isEqualTo(ExcludedSegments.NONE);

        // 상한 바로 아래(24.9km/h)는 빼지 않는다(경계값 자체는 ValidityRule.isOverspeed 시험)
        List<LocationPoint> limit =
                OverspeedFixtures.track(START, new double[] {0, 24.9 / 3.6, 2 * 24.9 / 3.6});
        assertThat(
                        new RunTrack(START, limit, List.of(), false)
                                .overspeedSegments(List.of(), ValidityRule.STANDARD)
                                .isEmpty())
                .isTrue();
    }

    @Test
    @DisplayName("3.14km 기록에 50m 순간이동 한 번: 옛 앱(구간 포함 선언)·새 앱(구간 제외 선언) 모두 3.09km·927초로 유효")
    void shortJump_isValidWithRemainingDistance() {
        List<LocationPoint> points = shortJump(START);
        for (long declared : new long[] {pathMeters(points), 3_090}) {
            RunRecord r = judged(upload(id(1), points, declared, Duration.ofSeconds(928)));
            assertThat(r.isValid()).isTrue();
            assertThat(r.getDistance()).isEqualTo(Distance.ofMeters(3_090));
            assertThat(r.getElapsed()).isEqualTo(Duration.ofSeconds(927));
            assertThat(r.getAveragePace()).isEqualTo(new Pace(300));
            assertThat(r.getExcludedDistance()).isEqualTo(Distance.ofMeters(50));
            assertThat(r.getExcludedElapsed()).isEqualTo(Duration.ofSeconds(1));
            assertThat(r.getScore()).isNotNull();
            assertThat(r.summary().distance()).isEqualTo(Distance.ofMeters(3_090));
        }
    }

    @Test
    @DisplayName("도달 시간·구간 페이스는 뺀 구간의 거리와 시간을 건너뛴다(최고 기록이 순간이동으로 빨라지지 않는다)")
    void shortJump_timeToReachSkipsExcludedPair() {
        RunRecord r = judged(upload(id(1), shortJump(START), 3_140, Duration.ofSeconds(928)));
        assertThat(r.timeToReach(Distance.ofKm(2))).contains(Duration.ofSeconds(600));
        assertThat(r.splitPaces()).containsExactly(new Pace(300), new Pace(300), new Pace(300));
    }

    @Test
    @DisplayName("순간이동만 있는 9078km 기록: 빼고 남은 360m라 무효, 저장 거리도 360m")
    void teleportOnly_isInvalid() {
        List<LocationPoint> points = teleportOnly(START);
        RunRecord r = judged(upload(id(2), points, pathMeters(points), Duration.ofSeconds(121)));
        assertThat(r.isValid()).isFalse();
        assertThat(r.getScore()).isNull();
        assertThat(r.getDistance()).isEqualTo(Distance.ofMeters(360));
        assertThat(r.getExcludedDistance().meters()).isBetween(9_077_000L, 9_079_000L);
    }

    @Test
    @DisplayName("차로 10km를 간 기록: 그 900초·10km만 빼고 3km·900초로 유효")
    void carRide_excludesLongSegment() {
        List<LocationPoint> points = carRide(START);
        RunRecord r = judged(upload(id(3), points, pathMeters(points), Duration.ofSeconds(1_800)));
        assertThat(r.isValid()).isTrue();
        assertThat(r.getDistance()).isEqualTo(Distance.ofMeters(3_000));
        assertThat(r.getElapsed()).isEqualTo(Duration.ofSeconds(900));
        assertThat(r.getExcludedDistance()).isEqualTo(Distance.ofMeters(10_000));
        assertThat(r.getExcludedElapsed()).isEqualTo(Duration.ofSeconds(900));
    }

    @Test
    @DisplayName("뺀 뒤 남은 값에 세 조건을 적용한다: 남은 거리가 1km 미만이면 무효")
    void remainingDistanceDecides() {
        // 900m 달리기 + 50m 순간이동 + 200m: 경로 1,150m지만 남은 1,100m … 1km 넘으므로 유효
        List<LocationPoint> enough =
                OverspeedFixtures.track(
                        START, ramp(new double[][] {{270, 10.0 / 3}, {1, 50}, {60, 10.0 / 3}}));
        assertThat(judged(upload(id(4), enough, 1_150, Duration.ofSeconds(331))).isValid())
                .isTrue();
        // 900m + 150m 순간이동: 경로 1,050m, 남은 900m라 무효
        List<LocationPoint> shortRun =
                OverspeedFixtures.track(START, ramp(new double[][] {{270, 10.0 / 3}, {1, 150}}));
        RunRecord r = judged(upload(id(5), shortRun, 1_050, Duration.ofSeconds(271)));
        assertThat(r.isValid()).isFalse();
        assertThat(r.getDistance()).isEqualTo(Distance.ofMeters(900));
    }

    @Test
    @DisplayName("초과 구간이 없으면 선언 값 그대로이고 제외량은 0이다")
    void noOverspeed_keepsDeclaredValues() {
        RunRecord r = RunFixtures.valid5k(id(6));
        assertThat(r.getDistance()).isEqualTo(Distance.ofKm(5));
        assertThat(r.getElapsed()).isEqualTo(Duration.ofSeconds(1_500));
        assertThat(r.getExcludedDistance()).isEqualTo(Distance.ZERO);
        assertThat(r.getExcludedElapsed()).isZero();
    }

    @Test
    @DisplayName("일회성 재판정은 판정된 무효 기록만 받는다(FR-043 재계산 금지의 좁은 예외)")
    void rejudgeInvalid_isNarrow() {
        RunRecord unjudged = RunRecord.receive(1L, RunFixtures.upload(id(7), 5_000, 1_500));
        assertThatThrownBy(() -> unjudged.rejudgeInvalid(ValidityRule.STANDARD))
                .isInstanceOf(IllegalStateException.class);
        RunRecord valid = RunFixtures.valid5k(id(8));
        assertThatThrownBy(() -> valid.rejudgeInvalid(ValidityRule.STANDARD))
                .isInstanceOf(IllegalStateException.class);
        RunRecord purged = judged(RunFixtures.upload(id(9), 900, 300));
        purged.purgeRoute();
        assertThatThrownBy(() -> purged.rejudgeInvalid(ValidityRule.STANDARD))
                .isInstanceOf(IllegalStateException.class);
        // judgeValidity의 한 번만 판정은 그대로다
        assertThatThrownBy(() -> valid.judgeValidity(ValidityRule.STANDARD))
                .isInstanceOf(IllegalStateException.class);
    }

    /** (초 수, 초속) 구간을 이어 붙인 위치(0초는 0m). */
    private static double[] ramp(double[][] legs) {
        int n = 1;
        for (double[] leg : legs) {
            n += (int) leg[0];
        }
        double[] out = new double[n];
        int i = 1;
        double at = 0;
        for (double[] leg : legs) {
            for (int s = 0; s < (int) leg[0]; s++) {
                at += leg[1];
                out[i++] = at;
            }
        }
        return out;
    }
}
