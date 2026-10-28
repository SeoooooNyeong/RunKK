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
