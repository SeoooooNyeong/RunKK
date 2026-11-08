package com.a207.runkk.runbody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.Distance;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.HeartRateSample;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.MotionSample;
import com.a207.runkk.running.PauseInterval;
import com.a207.runkk.running.RunBodyInput;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.StartDevice;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 05-19 D-444 RunBodyRequestFactory(7절 단위: 하프 7,200점). */
@DisplayName("런바디 분석 요청 만들기")
class RunBodyRequestFactoryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 7, 0);
    private static final String ID = RunFixtures.id(7);

    private static List<HeartRateSample> heartRates(LocalDateTime from, int seconds) {
        List<HeartRateSample> samples = new ArrayList<>();
        for (int i = 0; i <= seconds; i++) {
            samples.add(new HeartRateSample(from.plusSeconds(i), 140 + i % 20));
        }
        return samples;
    }

    private static RunBodyInput input(
            int seconds,
            long meters,
            List<HeartRateSample> heartRates,
            List<LocationPoint> points,
            List<PauseInterval> pauses,
            Duration paused) {
        return new RunBodyInput(
                ID,
                42,
                START,
                START.plusSeconds(seconds).plus(paused),
                Distance.ofMeters(meters),
                Duration.ofSeconds(seconds),
                StartDevice.WATCH,
                true,
                heartRates,
                points,
                pauses);
    }

    @Test
    void halfMarathon_7200Points_goInOneRequest() {
        int seconds = 7_200;
        RunBodyInput half =
                input(
                        seconds,
                        21_098,
                        heartRates(START, seconds),
                        RunFixtures.straightLine(START, 21_098.0 / seconds, seconds),
                        List.of(),
                        Duration.ZERO);

        RunBodyRequest request = RunBodyRequestFactory.of(half, 1995);

        assertThat(request.sessionId()).isEqualTo(ID);
        assertThat(request.userId()).isEqualTo("42");
        assertThat(request.source()).isEqualTo("watch");
        assertThat(request.startedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(request.activeMs()).isEqualTo(7_200_000);
        assertThat(request.distanceMeters()).isEqualTo(21_098);
        assertThat(request.birthYear()).isEqualTo(1995);
        assertThat(request.gps()).hasSize(7_201);
        List<RunBodyRequest.Sample> heart =
                request.samples().stream().filter(s -> s.heartRate() != null).toList();
        List<RunBodyRequest.Sample> moves =
                request.samples().stream().filter(s -> s.distance() != null).toList();
        assertThat(heart).hasSize(7_201);
        assertThat(moves).hasSize(7_200);
        assertThat(moves).allSatisfy(m -> assertThat(m.speed()).isBetween(2.8, 3.1));
        double total = moves.stream().mapToDouble(RunBodyRequest.Sample::distance).sum();
        assertThat(total).isBetween(21_000.0, 21_200.0);
        assertThat(request.samples()).isSortedAccordingTo((a, b) -> Long.compare(a.tMs(), b.tMs()));
        assertThat(request.samples().size()).isLessThanOrEqualTo(100_000);
        assertThat(request.recoveryHeartRates()).isEmpty();
    }

    @Test
    void pauses_areCutFromTheTimeAxis_andNotCountedAsMovement() {
        LocalDateTime pauseFrom = START.plusSeconds(100);
        LocalDateTime pauseTo = pauseFrom.plusSeconds(60);
        List<LocationPoint> points = new ArrayList<>();
        points.addAll(RunFixtures.straightLine(START, 3.0, 100));
        points.addAll(
                RunFixtures.straightLine(pauseTo, 3.0, 100).stream()
                        .map(
                                p ->
                                        new LocationPoint(
                                                p.measuredAt(),
                                                new com.a207.runkk.common.GeoPoint(
                                                        p.point().latitude() + 0.01,
                                                        p.point().longitude()),
                                                p.accuracyMeters(),
                                                p.signal()))
                        .toList());
        List<HeartRateSample> heart = heartRates(START, 260);
        RunBodyInput run =
                input(
                        200,
                        600,
                        heart,
                        points,
                        List.of(new PauseInterval(pauseFrom, pauseTo)),
                        Duration.ofSeconds(60));

        RunBodyRequest request = RunBodyRequestFactory.of(run, 1990);

        assertThat(request.activeMs()).isEqualTo(200_000);
        assertThat(request.gps()).as("정지 시작 점(벽시계 100초)은 정지 중이라 뺀다").hasSize(100 + 101);
        assertThat(request.gps())
                .as("정지 끝 점(벽시계 160초)은 운동 시간 100초")
                .filteredOn(g -> g.tMs() == 100_000L)
                .hasSize(1);
        assertThat(request.gps()).allSatisfy(g -> assertThat(g.tMs()).isLessThanOrEqualTo(200_000));
        assertThat(request.samples().stream().filter(s -> s.heartRate() != null))
                .as("정지 중(100~159초) 심박 60개는 뺀다")
                .hasSize(261 - 60);
        assertThat(request.samples().stream().filter(s -> s.distance() != null))
                .as("정지를 건너뛴 쌍(99초 → 160초)은 이동으로 세지 않는다")
                .hasSize(99 + 100);
        assertThat(request.samples())
                .filteredOn(s -> s.distance() != null)
                .allSatisfy(s -> assertThat(s.distance()).isLessThan(100.0));
    }

    @Test
    void heartRatesAfterEnd_becomeRecovery_withinFiveMinutes() {
        List<HeartRateSample> heart = new ArrayList<>(heartRates(START, 60));
        heart.add(new HeartRateSample(START.plusSeconds(120), 120));
        heart.add(new HeartRateSample(START.plusSeconds(60 + 301), 100));
        RunBodyInput run = input(60, 200, heart, List.of(), List.of(), Duration.ZERO);

        RunBodyRequest request = RunBodyRequestFactory.of(run, 1990);

        assertThat(request.recoveryHeartRates())
                .containsExactly(new RunBodyRequest.RecoveryHeartRate(60_000, 120));
        assertThat(request.gps()).isEmpty();
    }

    @Test
    void noHeartRateNoRoute_sendsOneEmptySample() {
        RunBodyInput run = input(600, 2_000, List.of(), List.of(), List.of(), Duration.ZERO);

        RunBodyRequest request = RunBodyRequestFactory.of(run, 1990);

        assertThat(request.samples())
                .containsExactly(new RunBodyRequest.Sample(0, null, null, null));
    }

    private static RunBodyInput withMotions(RunBodyInput base, List<MotionSample> motions) {
        return new RunBodyInput(
                base.recordId(),
                base.memberId(),
                base.startedAt(),
                base.endedAt(),
                base.distance(),
                base.elapsed(),
                base.startDevice(),
                base.valid(),
                base.heartRates(),
                base.points(),
                base.pauses(),
                motions);
    }

    @Test
    @DisplayName("기기가 잰 속도가 있으면 셈한 속도 대신 그 값을 보내고 거리는 위치로 보낸다")
    void measuredSpeed_replacesComputedSpeed_distanceStaysFromRoute() {
        int seconds = 120;
        List<MotionSample> motions = new ArrayList<>();
        for (int i = 0; i <= seconds; i++) {
            motions.add(new MotionSample(START.plusSeconds(i), 3.3333, 172.0));
        }
        RunBodyInput run =
                withMotions(
                        input(
                                seconds,
                                360,
                                List.of(),
                                RunFixtures.straightLine(START, 3.0, seconds),
                                List.of(),
                                Duration.ZERO),
                        motions);

        RunBodyRequest request = RunBodyRequestFactory.of(run, 1990);

        List<RunBodyRequest.Sample> device =
                request.samples().stream().filter(s -> s.cadence() != null).toList();
        assertThat(device).hasSize(seconds + 1);
        assertThat(device)
                .allSatisfy(
                        s -> {
                            assertThat(s.speed()).isEqualTo(3.333);
                            assertThat(s.cadence()).isEqualTo(172.0);
                            assertThat(s.distance()).isNull();
                        });
        List<RunBodyRequest.Sample> moves =
                request.samples().stream().filter(s -> s.distance() != null).toList();
        assertThat(moves).hasSize(seconds);
        assertThat(moves)
                .as("앞뒤 5초 안에 기기 속도가 있어 셈한 속도는 뺀다")
                .allSatisfy(m -> assertThat(m.speed()).isNull());
        assertThat(request.samples()).isSortedAccordingTo((a, b) -> Long.compare(a.tMs(), b.tMs()));
    }

    @Test
    @DisplayName("기기 속도가 없는 구간은 위치로 셈하고, 케이던스만 잰 표본은 케이던스만 보낸다")
    void computedSpeed_fillsGapsWithoutMeasuredSpeed_cadenceOnlySamplesKeepNullSpeed() {
        int seconds = 60;
        List<MotionSample> motions = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            motions.add(new MotionSample(START.plusSeconds(i), 3.0, null));
        }
        for (int i = 0; i <= seconds; i += 10) {
            motions.add(new MotionSample(START.plusSeconds(i), null, 165.5));
        }
        RunBodyInput run =
                withMotions(
                        input(
                                seconds,
                                180,
                                List.of(),
                                RunFixtures.straightLine(START, 3.0, seconds),
                                List.of(),
                                Duration.ZERO),
                        motions);

        RunBodyRequest request = RunBodyRequestFactory.of(run, 1990);

        List<RunBodyRequest.Sample> moves =
                request.samples().stream().filter(s -> s.distance() != null).toList();
        assertThat(moves)
                .filteredOn(m -> m.tMs() <= 25_000)
                .allSatisfy(m -> assertThat(m.speed()).isNull());
        assertThat(moves)
                .filteredOn(m -> m.tMs() > 25_000)
                .isNotEmpty()
                .allSatisfy(m -> assertThat(m.speed()).isBetween(2.9, 3.1));
        assertThat(request.samples())
                .filteredOn(s -> s.cadence() != null)
                .hasSize(7)
                .allSatisfy(s -> assertThat(s.speed()).isNull());
    }

    @Test
    @DisplayName("ai 상한 밖 값과 운동 시간 밖 표본은 뺀다")
    void outOfRangeOrOutsideActiveTime_motionsAreDropped() {
        LocalDateTime pauseFrom = START.plusSeconds(30);
        LocalDateTime pauseTo = pauseFrom.plusSeconds(30);
        List<MotionSample> motions =
                List.of(
                        new MotionSample(START.minusSeconds(1), 3.0, 170.0),
                        new MotionSample(START.plusSeconds(10), 16.0, 310.0),
                        new MotionSample(START.plusSeconds(11), 16.0, 171.0),
                        new MotionSample(START.plusSeconds(40), 3.0, 170.0),
                        new MotionSample(START.plusSeconds(200), 3.0, 170.0));
        RunBodyInput run =
                withMotions(
                        input(
                                60,
                                180,
                                List.of(),
                                List.of(),
                                List.of(new PauseInterval(pauseFrom, pauseTo)),
                                Duration.ofSeconds(30)),
                        motions);

        RunBodyRequest request = RunBodyRequestFactory.of(run, 1990);

        assertThat(request.samples())
                .containsExactly(new RunBodyRequest.Sample(11_000, null, null, null, 171.0));
    }

    @Test
    void create_rejectsMissingExcludedOrForeignRecord_andMissingBirthYear() {
        RunQueryService runs = mock(RunQueryService.class);
        MemberQueryService members = mock(MemberQueryService.class);
        RunBodyRequestFactory factory = new RunBodyRequestFactory(runs, members);
        RunBodyInput ok = input(600, 2_000, List.of(), List.of(), List.of(), Duration.ZERO);

        when(runs.bodyInputOf(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> factory.create(ID, 42))
                .isInstanceOf(RunBodyRejectedException.class)
                .hasMessage("RECORD_NOT_FOUND");

        when(runs.bodyInputOf(ID)).thenReturn(Optional.of(ok));
        assertThatThrownBy(() -> factory.create(ID, 43))
                .isInstanceOf(RunBodyRejectedException.class)
                .hasMessage("RECORD_OWNER_MISMATCH");

        when(members.birthYearOf(42)).thenReturn(OptionalInt.empty());
        assertThatThrownBy(() -> factory.create(ID, 42))
                .isInstanceOf(RunBodyRejectedException.class)
                .hasMessage("BIRTH_YEAR_MISSING");

        when(members.birthYearOf(42)).thenReturn(OptionalInt.of(2000));
        assertThat(factory.create(ID, 42).birthYear()).isEqualTo(2000);

        RunBodyInput excluded =
                new RunBodyInput(
                        ID,
                        42,
                        START,
                        START.plusMinutes(10),
                        Distance.ofMeters(2_000),
                        Duration.ofMinutes(10),
                        StartDevice.PHONE,
                        false,
                        List.of(),
                        List.of(),
                        List.of());
        when(runs.bodyInputOf(ID)).thenReturn(Optional.of(excluded));
        assertThatThrownBy(() -> factory.create(ID, 42))
                .isInstanceOf(RunBodyRejectedException.class)
                .hasMessage("RECORD_EXCLUDED");
    }
}
