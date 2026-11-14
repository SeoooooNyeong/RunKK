package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** running 시험용 입력. 북쪽으로 곧게, 1초마다 같은 속도로 달린 기록을 만든다. */
public final class RunFixtures {

    public static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 7, 0);

    /** 지구 반지름 6,371,000m에서 위도 1도의 길이. */
    public static final double METERS_PER_DEGREE = 2 * Math.PI * 6_371_000 / 360;

    private RunFixtures() {}

    public static String id(int n) {
        return String.format(Locale.ROOT, "00000000-0000-4000-8000-%012d", n);
    }

    public static List<LocationPoint> straightLine(
            LocalDateTime start, double metersPerSecond, int seconds) {
        List<LocationPoint> points = new ArrayList<>();
        for (int i = 0; i <= seconds; i++) {
            points.add(
                    new LocationPoint(
                            start.plusSeconds(i),
                            new GeoPoint(37.5 + i * metersPerSecond / METERS_PER_DEGREE, 127.0),
                            5.0,
                            SignalState.NORMAL));
        }
        return points;
    }

    public static RunUpload upload(String id, LocalDateTime start, long meters, int seconds) {
        List<LocationPoint> points = straightLine(start, (double) meters / seconds, seconds);
        return new RunUpload(
                id,
                start,
                start.plusSeconds(seconds),
                Distance.ofMeters(meters),
                Duration.ofSeconds(seconds),
                RunMode.FREE,
                CardKind.FREE_RUN,
                null,
                null,
                points.size(),
                points,
                List.of(),
                List.of());
    }

    public static RunUpload upload(String id, long meters, int seconds) {
        return upload(id, START, meters, seconds);
    }

    public static RunUpload withHeartRates(RunUpload u, List<HeartRateSample> samples) {
        return new RunUpload(
                u.recordId(),
                u.startedAt(),
                u.endedAt(),
                u.distance(),
                u.elapsed(),
                u.mode(),
                u.cardKind(),
                u.battleId(),
                u.crewRunPostId(),
                u.declaredPointCount(),
                u.points(),
                u.unstableSegments(),
                samples);
    }

    /** 시작 기기만 바꾼 사본(워치 단독 기록 WATCH, W23). */
    public static RunUpload withStartDevice(RunUpload u, StartDevice device) {
        return new RunUpload(
                u.recordId(),
                u.startedAt(),
                u.endedAt(),
                u.distance(),
                u.elapsed(),
                u.mode(),
                u.cardKind(),
                u.battleId(),
                u.crewRunPostId(),
                u.declaredPointCount(),
                u.points(),
                u.unstableSegments(),
                u.heartRates(),
                u.pauses(),
                device,
                u.motions());
    }

    /** 기기가 잰 속도·케이던스를 단 사본(S15P31A207-242). */
    public static RunUpload withMotions(RunUpload u, List<MotionSample> motions) {
        return new RunUpload(
                u.recordId(),
                u.startedAt(),
                u.endedAt(),
                u.distance(),
                u.elapsed(),
                u.mode(),
                u.cardKind(),
                u.battleId(),
                u.crewRunPostId(),
                u.declaredPointCount(),
                u.points(),
                u.unstableSegments(),
                u.heartRates(),
                u.pauses(),
                u.startDevice(),
                motions);
    }

    /**
     * 한 번 멈춘 크루런 기록(FR-300, MR !91 BR-1). 운동 runSeconds초 동안 meters를 곧게 달리되 pauseAfter초 지점에서
     * pauseSeconds초 멈춘다(그 사이 포인트 없음, 재개 시각에 같은 자리 포인트 하나). 종료 = 시작 + 운동 + 정지, elapsed = 운동 시간.
     */
    public static RunUpload pausedCrewRun(
            String id, LocalDateTime start, long postId, long meters, int runSeconds, Pause pause) {
        double speed = (double) meters / runSeconds;
        List<LocationPoint> points = new ArrayList<>();
        for (int i = 0; i <= runSeconds; i++) {
            long at = i <= pause.after() ? i : i + (long) pause.seconds();
            points.add(pointAt(start.plusSeconds(at), i * speed));
            if (i == pause.after()) {
                points.add(pointAt(start.plusSeconds(at + pause.seconds()), i * speed));
            }
        }
        LocalDateTime from = start.plusSeconds(pause.after());
        return new RunUpload(
                id,
                start,
                start.plusSeconds((long) runSeconds + pause.seconds()),
                Distance.ofMeters(meters),
                Duration.ofSeconds(runSeconds),
                RunMode.CREW_RUN,
                CardKind.CREW_RUN,
                null,
                postId,
                points.size(),
                points,
                List.of(),
                List.of(),
                List.of(new PauseInterval(from, from.plusSeconds(pause.seconds()))));
    }

    /** {@link #pausedCrewRun}의 정지: 운동 after초 지점에서 seconds초. */
    public record Pause(int after, int seconds) {}

    private static LocationPoint pointAt(LocalDateTime at, double northMeters) {
        return new LocationPoint(
                at,
                new GeoPoint(37.5 + northMeters / METERS_PER_DEGREE, 127.0),
                5.0,
                SignalState.NORMAL);
    }

    /** 5km 25:00(유효, VDOT 약 38.3). */
    public static RunRecord valid5k(String id) {
        return judged(upload(id, 5_000, 1_500));
    }

    public static RunRecord judged(RunUpload upload) {
        RunRecord r = RunRecord.receive(1L, upload);
        r.judgeValidity(ValidityRule.STANDARD);
        return r;
    }
}
