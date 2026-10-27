package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.METERS_PER_DEGREE;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 초과 시속 구간(FR-047 v0.10) 시험 입력. 1초마다 포인트를 찍고 남쪽(위도 감소)으로 움직인다. 사용자가 에뮬레이터에서 본 기록을 줄여 흉내 낸다: 3.14km
 * 기록에 50m 순간이동 한 번(유효 3.09km), 순간이동만 있는 9078km 기록(무효), 중간에 차로 10km를 간 기록.
 */
public final class OverspeedFixtures {

    /** 5:00/km 달리기 속도(m/s). */
    public static final double RUN_MPS = 10.0 / 3;

    private OverspeedFixtures() {}

    /** start부터 1초 간격 포인트. meters[i]는 i초 뒤 시작점에서 남쪽으로 간 거리다. */
    public static List<LocationPoint> track(LocalDateTime start, double[] meters) {
        List<LocationPoint> points = new ArrayList<>();
        for (int i = 0; i < meters.length; i++) {
            points.add(
                    new LocationPoint(
                            start.plusSeconds(i),
                            new GeoPoint(37.5 - meters[i] / METERS_PER_DEGREE, 127.0),
                            5.0,
                            SignalState.NORMAL));
        }
        return points;
    }

    /** 구간들을 이어 붙인 위치 목록. 구간마다 (초 수, 초속)이고 jump는 그 초에 순간이동한 거리다. */
    private static double[] legs(double[][] legs) {
        List<Double> out = new ArrayList<>();
        double at = 0;
        out.add(at);
        for (double[] leg : legs) {
            for (int i = 0; i < (int) leg[0]; i++) {
                at += leg[1];
                out.add(at);
            }
        }
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** 1,500m(450초) → 1초에 50m 순간이동(180km/h) → 1,590m(477초). 경로 3,140m, 뺀 뒤 3,090m·927초. */
    public static List<LocationPoint> shortJump(LocalDateTime start) {
        return track(start, legs(new double[][] {{450, RUN_MPS}, {1, 50}, {477, RUN_MPS}}));
    }

    /** 180m(60초) → 1초에 9,078km 순간이동 → 180m(60초). 뺀 뒤 360m라 무효. */
    public static List<LocationPoint> teleportOnly(LocalDateTime start) {
        return track(start, legs(new double[][] {{60, 3}, {1, 9_078_000}, {60, 3}}));
    }

    /** 2,000m(600초) → 차로 10,000m(900초, 40km/h) → 1,000m(300초). 뺀 뒤 3,000m·900초. */
    public static List<LocationPoint> carRide(LocalDateTime start) {
        return track(
                start,
                legs(new double[][] {{600, RUN_MPS}, {900, 10_000 / 900.0}, {300, RUN_MPS}}));
    }

    /** 위치 목록으로 만든 자유런 업로드. 시작~종료는 첫·끝 포인트, 선언 거리·시간은 인자다(옛 앱은 초과 구간을 더해 보냈다). */
    public static RunUpload upload(
            String id, List<LocationPoint> points, long declaredMeters, Duration elapsed) {
        return new RunUpload(
                id,
                points.get(0).measuredAt(),
                points.get(points.size() - 1).measuredAt(),
                Distance.ofMeters(declaredMeters),
                elapsed,
                RunMode.FREE,
                CardKind.FREE_RUN,
                null,
                null,
                points.size(),
                points,
                List.of(),
                List.of());
    }

    /** 경로 전체 길이(초과 구간 포함, 옛 앱이 선언했을 거리). */
    public static long pathMeters(List<LocationPoint> points) {
        return new RunTrack(points.get(0).measuredAt(), points, List.of(), false)
                .pathLength()
                .meters();
    }
}
