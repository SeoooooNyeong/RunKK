package com.a207.runkk.runimal;

import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.policy.CourseSimilarityRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 코스 동일성 판정(설계 6.1.8, A-15, FR-166, 결정 Q-80). 새 경로를 10m 간격으로 다시 표본화하고, 이전 경로마다 반경 크기 격자 칸에 넣은 해시로
 * 이웃 9칸만 본다. 새 포인트의 coverage(80%) 이상이 이전 어느 한 코스의 radius(50m) 안이면 같은 코스다. 기록 하나당 O(30 × n).
 */
@Component
public class CourseSimilarityJudge {

    /** 다시 표본화하는 간격(A-15). */
    static final long RESAMPLE_METERS = 10;

    private static final double METERS_PER_DEGREE_LAT = 110_540.0;
    private static final double METERS_PER_DEGREE_LON = 111_320.0;

    public boolean isSameCourse(
            List<GeoPoint> track, List<List<GeoPoint>> previous, CourseSimilarityRule rule) {
        List<GeoPoint> sampled = resample(track);
        if (sampled.isEmpty()) {
            return false;
        }
        for (List<GeoPoint> course : previous) {
            if (!course.isEmpty() && coverage(sampled, course, rule) >= rule.coverage()) {
                return true;
            }
        }
        return false;
    }

    /** 마지막으로 남긴 점에서 10m 이상 떨어진 점만 남긴다. */
    static List<GeoPoint> resample(List<GeoPoint> track) {
        List<GeoPoint> kept = new ArrayList<>();
        for (GeoPoint point : track) {
            if (kept.isEmpty()
                    || kept.get(kept.size() - 1).distanceTo(point).meters() >= RESAMPLE_METERS) {
                kept.add(point);
            }
        }
        return kept;
    }

    private static double coverage(
            List<GeoPoint> sampled, List<GeoPoint> course, CourseSimilarityRule rule) {
        Grid grid = new Grid(course, rule.radiusMeters());
        long near =
                sampled.stream().filter(p -> grid.hasPointWithin(p, rule.radiusMeters())).count();
        return (double) near / sampled.size();
    }

    /** 반경 크기 격자 칸 → 포인트 목록. 위도 기준 평면 근사로 칸을 정하고 실제 거리는 하버사인으로 잰다. */
    private static final class Grid {

        private static final long KEY_STRIDE = 1_000_003L;

        private final Map<Long, List<GeoPoint>> cells = new HashMap<>();
        private final double cellMeters;
        private final double lonScale;

        Grid(List<GeoPoint> points, double cellMeters) {
            this.cellMeters = cellMeters;
            this.lonScale = Math.cos(Math.toRadians(points.get(0).latitude()));
            points.forEach(
                    p ->
                            cells.computeIfAbsent(key(cellX(p), cellY(p)), k -> new ArrayList<>())
                                    .add(p));
        }

        boolean hasPointWithin(GeoPoint point, int radiusMeters) {
            long column = cellX(point);
            long row = cellY(point);
            for (long columnStep = -1; columnStep <= 1; columnStep++) {
                for (long rowStep = -1; rowStep <= 1; rowStep++) {
                    List<GeoPoint> bucket =
                            cells.getOrDefault(key(column + columnStep, row + rowStep), List.of());
                    if (bucket.stream()
                            .anyMatch(near -> near.distanceTo(point).meters() <= radiusMeters)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private long cellX(GeoPoint point) {
            return (long)
                    Math.floor(point.longitude() * METERS_PER_DEGREE_LON * lonScale / cellMeters);
        }

        private long cellY(GeoPoint point) {
            return (long) Math.floor(point.latitude() * METERS_PER_DEGREE_LAT / cellMeters);
        }

        private static long key(long column, long row) {
            return column * KEY_STRIDE + row;
        }
    }
}
