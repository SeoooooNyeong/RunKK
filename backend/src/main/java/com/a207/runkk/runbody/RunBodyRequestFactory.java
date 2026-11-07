package com.a207.runkk.runbody;

import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.ServerZone;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.HeartRateSample;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.PauseInterval;
import com.a207.runkk.running.RunBodyInput;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.StartDevice;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * 저장된 러닝으로 ai 런바디 분석 요청을 만든다(설계 05-19 2.2, D-444). 시각은 정지 구간을 뺀 운동 시간 축 밀리초다(ai는 activeMs까지 1초 칸을
 * 만든다). 케이던스는 저장하지 않아 보내지 않는다. 속도도 저장하지 않지만, 이웃한 위치 점 사이 거리와 시간으로 셈한 이동 표본(speed·distance)을 보낸다.
 * 이것이 없으면 ai가 모든 칸을 "멈춤"으로 봐 지구력·안정성·체력 축을 낼 수 없다(설계와 다른 점, 구현 보고에 적음). 회원 식별 항목은 회원 ID 문자열뿐이다.
 */
@Component
public class RunBodyRequestFactory {

    /** ai SampleIn.distance 상한(lt 100m). 넘는 쌍은 위치 튐으로 보고 버린다. */
    static final double MAX_STEP_METERS = 100.0;

    /** ai SampleIn.speed 상한(lt 15m/s). */
    static final double MAX_SPEED_MPS = 15.0;

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
                movement(previous.point(), point.point(), t.getAsLong() - previousT)
                        .ifPresent(
                                step ->
                                        samples.add(
                                                new RunBodyRequest.Sample(
                                                        t.getAsLong(),
                                                        null,
                                                        step.speed(),
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
