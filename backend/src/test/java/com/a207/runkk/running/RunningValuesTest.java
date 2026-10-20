package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.Pace;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** RunningScore VDOT·역산(A-01), ValidityRule, HeartRateSeries, HeartRateZone, 값 검증(14장 순서 1). */
class RunningValuesTest {

    @Test
    void runningScore_vdotOf5kIn25Minutes() {
        RunningScore score = RunningScore.of(Distance.ofKm(5), Duration.ofMinutes(25));
        assertThat(score.value()).isCloseTo(38.3, within(0.05));
    }

    @Test
    void runningScore_fasterIsHigher_andPreconditions() {
        double slow = RunningScore.of(Distance.ofKm(10), Duration.ofMinutes(60)).value();
        double fast = RunningScore.of(Distance.ofKm(10), Duration.ofMinutes(45)).value();
        assertThat(fast).isGreaterThan(slow);
        assertThatThrownBy(() -> RunningScore.of(Distance.ofMeters(999), Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RunningScore.of(Distance.ofKm(1), Duration.ofSeconds(179)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runningScore_paceFor_invertsTheFormula() {
        RunningScore score = RunningScore.of(Distance.ofKm(5), Duration.ofMinutes(25));
        assertThat(score.paceFor(Distance.ofKm(5)).secondsPerKm()).isCloseTo(300, within(1));
        Pace tenK = score.paceFor(Distance.ofKm(10));
        assertThat(tenK.secondsPerKm()).isGreaterThan(300);
        double back =
                RunningScore.of(Distance.ofKm(10), Duration.ofSeconds(tenK.secondsPerKm() * 10L))
                        .value();
        assertThat(back).isCloseTo(score.value(), within(0.1));
        assertThat(new RunningScore(48).paceFor(Distance.ofKm(1)).secondsPerKm())
                .isLessThan(new RunningScore(27).paceFor(Distance.ofKm(1)).secondsPerKm());
    }

    @Test
    void runningScore_average() {
        assertThat(RunningScore.average(List.of(new RunningScore(30), new RunningScore(40))))
                .isEqualTo(new RunningScore(35));
        assertThatThrownBy(() -> RunningScore.average(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validityRule_fourConditions() {
        ValidityRule rule = ValidityRule.STANDARD;
        Distance d = Distance.ofKm(5);
        Duration t = Duration.ofMinutes(25);
        Pace p = new Pace(300);
        assertThat(rule.isSatisfiedBy(d, t, p, 20)).isTrue();
        assertThat(rule.isSatisfiedBy(Distance.ofMeters(999), t, p, 20)).isFalse();
        assertThat(rule.isSatisfiedBy(d, Duration.ofSeconds(179), p, 20)).isFalse();
        assertThat(rule.isSatisfiedBy(d, t, new Pace(179), 20)).isFalse();
        assertThat(rule.isSatisfiedBy(d, t, new Pace(901), 20)).isFalse();
        assertThat(rule.isSatisfiedBy(d, t, new Pace(180), 25)).isTrue();
        assertThat(rule.isSatisfiedBy(d, t, new Pace(900), 25.01)).isFalse();
    }

    @Test
    void heartRateZone_boundaries() {
        assertThat(HeartRateZone.of(99, 200)).isEmpty();
        assertThat(HeartRateZone.of(100, 200)).contains(HeartRateZone.Z1);
        assertThat(HeartRateZone.of(140, 200)).contains(HeartRateZone.Z3);
        assertThat(HeartRateZone.of(180, 200)).contains(HeartRateZone.Z5);
    }

    @Test
    void heartRateSeries_summaryZonesPurgeAndRoundTrip() {
        HeartRateSeries series =
                HeartRateSeries.of(
                        START,
                        List.of(
                                new HeartRateSample(START.plusSeconds(10), 150),
                                new HeartRateSample(START, 120),
                                new HeartRateSample(START.plusSeconds(20), 181)));
        assertThat(series.summary()).isEqualTo(new HeartRateSummary(150, 181));
        Map<HeartRateZone, Duration> zones = series.zoneDistribution(200);
        assertThat(zones)
                .containsEntry(HeartRateZone.Z2, Duration.ofSeconds(10))
                .containsEntry(HeartRateZone.Z3, Duration.ofSeconds(10))
                .containsEntry(HeartRateZone.Z5, Duration.ofSeconds(1));
        assertThat(series.zoneDistribution(0)).isEmpty();

        List<HeartRateSample> decoded =
                HeartRateSeries.decodeSamples(START, series.encodeSamples());
        assertThat(decoded).isEqualTo(series.samples());

        series.append(List.of(new HeartRateSample(START.plusSeconds(30), 90)));
        assertThat(series.summary().average()).isEqualTo(135);
        series.purge();
        assertThat(series.samples()).isEmpty();
        assertThat(series.summary()).isEqualTo(new HeartRateSummary(135, 181));
        assertThatThrownBy(() -> series.append(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void valueObjects_rejectBadInput() {
        assertThatThrownBy(() -> new HeartRateSample(START, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new LocationPoint(START, new GeoPoint(0, 0), -1, SignalState.NORMAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnstableSegment(START, START.minusSeconds(1), Distance.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Weather(10, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new NewRecordMark(
                                        NewRecordMark.Kind.LONGEST,
                                        com.a207.runkk.common.StandardDistance.KM5,
                                        "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunUploadCommand(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateHeartRateCommand(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CardKind.SELF_RUN.belongsTo(RunMode.BATTLE)).isTrue();
        assertThat(CardKind.CREW_RUN.belongsTo(RunMode.FREE)).isFalse();
    }
}
