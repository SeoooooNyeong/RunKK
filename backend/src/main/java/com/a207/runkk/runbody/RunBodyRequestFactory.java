package com.a207.runkk.runbody;

import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.ServerZone;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.HeartRateSample;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.MotionSample;
import com.a207.runkk.running.PauseInterval;
import com.a207.runkk.running.RunBodyInput;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.StartDevice;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * 저장된 러닝으로 ai 런바디 분석 요청을 만든다(설계 05-19 2.2, D-444). 시각은 정지 구간을 뺀 운동 시간 축 밀리초다(ai는 activeMs까지 1초 칸을
 * 만든다). 회원 식별 항목은 회원 ID 문자열뿐이다.
 *
 * <p>속도·케이던스는 "기기가 잰 값이 있으면 그 값, 없으면 계산한 값"이다(2026-11-08 사용자 결정, S15P31A207-242, D-444를 바꾼다).
 *
 * <ul>
 *   <li>기기가 잰 속도·케이던스(RunBodyInput.motions)는 그대로 표본으로 보낸다. ai 상한 밖 값은 그 값만 뺀다.
 *   <li>이웃한 위치 점 사이 거리는 늘 이동 표본(distance)으로 보낸다. 속도는 그 시각 앞뒤 {@link #MEASURED_SPEED_WINDOW} 안에 기기가
 *       잰 속도가 없을 때만 거리와 시간으로 셈해 함께 보낸다. 이것이 없으면 ai가 모든 칸을 "멈춤"으로 봐 지구력·안정성·체력 축을 낼 수 없다.
 *   <li>케이던스는 계산하지 않는다. 기기가 재지 않았으면 비우고 ai가 no_cadence를 붙인다.
 * </ul>
 */
@Component
public class RunBodyRequestFactory {

    /** ai SampleIn.distance 상한(lt 100m). 넘는 쌍은 위치 튐으로 보고 버린다. */
    static final double MAX_STEP_METERS = 100.0;

    /** ai SampleIn.speed 상한(lt 15m/s). */
    static final double MAX_SPEED_MPS = 15.0;

    /** ai SampleIn.cadence 상한(lt 300spm). */
    static final double MAX_CADENCE_SPM = 300.0;

    /** 위치 점 시각 앞뒤로 이 안에 기기가 잰 속도가 있으면 셈한 속도를 보내지 않는다(두 값을 한 칸에 섞지 않는다). */
    static final Duration MEASURED_SPEED_WINDOW = Duration.ofSeconds(5);

    /** 종료 뒤 회복 심박으로 보낼 시간 범위(ai는 1·2분 뒤 값을 본다). */
    static final Duration RECOVERY_WINDOW = Duration.ofMinutes(5);

    /** ai recoveryHeartRates 상한. */
    static final int MAX_RECOVERY = 1_000;

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final RunQueryService runQuery;
    private final MemberQueryService memberQuery;

    public RunBodyRequestFactory(RunQueryService runQuery, MemberQueryService memberQuery) {
        this.runQuery = runQuery;
        this.memberQuery = memberQuery;
    }

    /**
     * 요청을 만든다.
     *
     * @throws RunBodyRejectedException 기록이 없거나(지움) 남의 기록이거나 집계 제외 기록이거나 출생연도가 없다
     */
    public RunBodyRequest create(String recordId, long memberId) {
        RunBodyInput input =
                runQuery.bodyInputOf(recordId)
                        .orElseThrow(() -> new RunBodyRejectedException("RECORD_NOT_FOUND"));
        if (input.memberId() != memberId) {
            throw new RunBodyRejectedException("RECORD_OWNER_MISMATCH");
        }
        if (!input.valid()) {
            throw new RunBodyRejectedException("RECORD_EXCLUDED");
        }
        OptionalInt birthYear = memberQuery.birthYearOf(memberId);
        if (birthYear.isEmpty()) {
            throw new RunBodyRejectedException("BIRTH_YEAR_MISSING");
        }
        return of(input, birthYear.getAsInt());
    }

    /** 재료로 요청을 만든다(시험이 직접 부른다). */
    static RunBodyRequest of(RunBodyInput input, int birthYear) {
        ActiveClock clock = new ActiveClock(input.startedAt(), input.endedAt(), input.pauses());
        List<RunBodyRequest.Sample> samples = new ArrayList<>();
        List<RunBodyRequest.RecoveryHeartRate> recovery = new ArrayList<>();
        for (HeartRateSample hr : input.heartRates()) {
            if (hr.measuredAt().isAfter(input.endedAt())) {
                long after = Duration.between(input.endedAt(), hr.measuredAt()).toMillis();
                if (after <= RECOVERY_WINDOW.toMillis() && recovery.size() < MAX_RECOVERY) {
                    recovery.add(new RunBodyRequest.RecoveryHeartRate(after, hr.bpm()));
                }
                continue;
            }
            clock.activeMillis(hr.measuredAt())
                    .ifPresent(
                            t -> samples.add(new RunBodyRequest.Sample(t, hr.bpm(), null, null)));
        }
        long[] measuredSpeeds = addMeasuredMotions(input.motions(), clock, samples);
        List<RunBodyRequest.GpsPoint> gps = new ArrayList<>();
        LocationPoint previous = null;
        long previousT = -1;
        for (LocationPoint point : input.points()) {
            OptionalLong t = clock.activeMillis(point.measuredAt());
            if (t.isEmpty()) {
                previous = null;
                continue;
            }
            gps.add(
                    new RunBodyRequest.GpsPoint(
                            t.getAsLong(),
                            point.point().latitude(),
                            point.point().longitude(),
                            point.accuracyMeters()));
            if (previous != null
                    && !clock.pausedBetween(previous.measuredAt(), point.measuredAt())) {
                boolean measured = hasNear(measuredSpeeds, t.getAsLong());
                movement(previous.point(), point.point(), t.getAsLong() - previousT)
                        .ifPresent(
                                step ->
                                        samples.add(
                                                new RunBodyRequest.Sample(
                                                        t.getAsLong(),
                                                        null,
                                                        measured ? null : step.speed(),
                                                        step.meters())));
            }
            previous = point;
            previousT = t.getAsLong();
        }
        samples.sort(Comparator.comparingLong(RunBodyRequest.Sample::tMs));
        if (samples.isEmpty()) {
            // ai SessionIn.samples는 1개 이상이다. 심박·위치가 없는 기록은 값 없는 표본 하나로 요약만 받는다(qualityFlags no_gps
            // 등)
            samples.add(new RunBodyRequest.Sample(0, null, null, null));
        }
        return new RunBodyRequest(
                input.recordId(),
                Long.toString(input.memberId()),
                input.startDevice() == StartDevice.WATCH ? "watch" : "phone",
                input.startedAt().atZone(ServerZone.ID).toOffsetDateTime(),
                input.endedAt().atZone(ServerZone.ID).toOffsetDateTime(),
                input.elapsed().toMillis(),
                input.distance().meters(),
                birthYear,
                samples,
                gps,
                recovery);
    }

    /**
     * 기기가 잰 속도·케이던스를 표본으로 더한다. 운동 시간 밖(시작 전·종료 뒤·정지 중) 표본은 버리고, ai 상한 밖 값은 그 값만 뺀다.
     *
     * @return 속도를 보낸 표본의 운동 시간 축 밀리초(오름차순)
     */
    private static long[] addMeasuredMotions(
            List<MotionSample> motions, ActiveClock clock, List<RunBodyRequest.Sample> samples) {
        List<Long> speedTimes = new ArrayList<>();
        for (MotionSample motion : motions) {
            OptionalLong t = clock.activeMillis(motion.measuredAt());
            if (t.isEmpty()) {
                continue;
            }
            Double speed = below(motion.speedMps(), MAX_SPEED_MPS, 3);
            Double cadence = below(motion.cadenceSpm(), MAX_CADENCE_SPM, 1);
            if (speed == null && cadence == null) {
                continue;
            }
            samples.add(new RunBodyRequest.Sample(t.getAsLong(), null, speed, null, cadence));
            if (speed != null) {
                speedTimes.add(t.getAsLong());
            }
        }
        return speedTimes.stream().mapToLong(Long::longValue).sorted().toArray();
    }

    /** 0 이상 상한 미만이면 반올림한 값, 아니면 null. */
    private static Double below(Double value, double limit, int decimals) {
        if (value == null || value < 0 || value >= limit) {
            return null;
        }
        return round(value, decimals);
    }

    /** sorted 안에 t 앞뒤 MEASURED_SPEED_WINDOW 안의 값이 있는가. */
    private static boolean hasNear(long[] sorted, long t) {
        long window = MEASURED_SPEED_WINDOW.toMillis();
        int i = Arrays.binarySearch(sorted, t - window);
        int from = i >= 0 ? i : -i - 1;
        return from < sorted.length && sorted[from] <= t + window;
    }

    /** 이웃한 두 위치 점 사이 이동. 시간이 0 이하이거나 ai 상한을 넘는 쌍은 비어 있다. */
    private static Optional<Step> movement(GeoPoint from, GeoPoint to, long millis) {
        if (millis <= 0) {
            return Optional.empty();
        }
        double meters = haversine(from, to);
        double speed = meters / (millis / 1000.0);
        if (meters >= MAX_STEP_METERS || speed >= MAX_SPEED_MPS) {
            return Optional.empty();
        }
        return Optional.of(new Step(round(meters, 3), round(speed, 3)));
    }

    private record Step(double meters, double speed) {}

    /** GeoPoint.distanceTo는 미터 정수로 반올림해 1초 간격 거리(수 m)가 크게 틀어진다. 같은 공식을 실수로 셈한다. */
    static double haversine(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h =
                Math.pow(Math.sin(dLat / 2), 2)
                        + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    /** 벽시계 일시 → 정지 구간을 뺀 운동 시간 축 밀리초. 시작 전·종료 뒤·정지 중은 비어 있다. */
    static final class ActiveClock {
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final List<PauseInterval> pauses;

        ActiveClock(LocalDateTime start, LocalDateTime end, List<PauseInterval> pauses) {
            this.start = start;
            this.end = end;
            this.pauses =
                    pauses.stream().sorted(Comparator.comparing(PauseInterval::from)).toList();
        }

        OptionalLong activeMillis(LocalDateTime at) {
            if (at.isBefore(start) || at.isAfter(end)) {
                return OptionalLong.empty();
            }
            long paused = 0;
            for (PauseInterval pause : pauses) {
                if (!pause.to().isAfter(at)) {
                    paused += Duration.between(pause.from(), pause.to()).toMillis();
                } else if (!pause.from().isAfter(at)) {
                    return OptionalLong.empty();
                }
            }
            return OptionalLong.of(Duration.between(start, at).toMillis() - paused);
        }

        /** 두 일시 사이에 정지 구간이 시작되었는가(그 쌍의 이동은 정지를 건너뛴 것이라 세지 않는다). */
        boolean pausedBetween(LocalDateTime from, LocalDateTime to) {
            for (PauseInterval pause : pauses) {
                if (pause.from().isAfter(from) && !pause.from().isAfter(to)) {
                    return true;
                }
            }
            return false;
        }
    }
}
