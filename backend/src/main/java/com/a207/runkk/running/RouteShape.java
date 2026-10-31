package com.a207.runkk.running;

import com.a207.runkk.common.GeoPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 기록 목록 썸네일용 경로 모양(설계 5.6.2 running.RouteShape, D-234). 경로를 최대 24점으로 줄이고 0~1 상자로 정규화해 위경도가 남지 않는다.
 * 가로·세로 비율은 유지하고(긴 쪽이 0~1) 짧은 쪽은 가운데에 둔다. x는 동쪽, y는 북쪽으로 커진다.
 *
 * @param points 정규화한 점(최대 24개, 2점 미만 경로면 빈 목록)
 */
public record RouteShape(List<ShapePoint> points) {

    /** 썸네일 점 수 상한. */
    public static final int MAX_POINTS = 24;

    private static final String POINT_SEPARATOR = ";";
    private static final String AXIS_SEPARATOR = ",";

    public RouteShape {
        points = List.copyOf(points);
        if (points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("모양 점은 24개까지다: " + points.size());
        }
    }

    /** 경로를 모양으로 줄인다. 2점 미만이면 빈 모양이다. 같은 좌표만 있으면 가운데 한 점들로 정규화한다(0 나누기 없음). */
    public static RouteShape of(List<GeoPoint> route) {
        if (route == null || route.size() < 2) {
            return new RouteShape(List.of());
        }
        List<GeoPoint> picked = pick(route);
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (GeoPoint p : picked) {
            minLat = Math.min(minLat, p.latitude());
            maxLat = Math.max(maxLat, p.latitude());
            minLon = Math.min(minLon, p.longitude());
            maxLon = Math.max(maxLon, p.longitude());
        }
        // 경도 1도의 거리는 위도에 따라 줄어든다. 가로를 cos(위도)로 줄여 모양 비율을 맞춘다
        double lonScale = Math.cos(Math.toRadians((minLat + maxLat) / 2));
        double width = (maxLon - minLon) * lonScale;
        double height = maxLat - minLat;
        double span = Math.max(width, height);
        List<ShapePoint> shaped = new ArrayList<>();
        for (GeoPoint p : picked) {
            double x =
                    span == 0
                            ? 0.5
                            : ((p.longitude() - minLon) * lonScale + (span - width) / 2) / span;
            double y = span == 0 ? 0.5 : ((p.latitude() - minLat) + (span - height) / 2) / span;
            shaped.add(new ShapePoint(round(x), round(y)));
        }
        return new RouteShape(shaped);
    }

    /** 첫 점과 끝 점을 남기고 고르게 MAX_POINTS개 이하로 고른다. */
    private static List<GeoPoint> pick(List<GeoPoint> route) {
        if (route.size() <= MAX_POINTS) {
            return route;
        }
        List<GeoPoint> picked = new ArrayList<>(MAX_POINTS);
        double step = (route.size() - 1) / (double) (MAX_POINTS - 1);
        for (int i = 0; i < MAX_POINTS; i++) {
            picked.add(route.get((int) Math.round(i * step)));
        }
        return picked;
    }

    private static double round(double v) {
        double clamped = Math.max(0, Math.min(1, v));
        return Math.round(clamped * 1000) / 1000.0;
    }

    /** 저장 형식 "x,y;x,y…"(소수 셋째 자리). 빈 모양은 빈 문자열이다. */
    public String encode() {
        StringBuilder text = new StringBuilder();
        for (ShapePoint p : points) {
            if (!text.isEmpty()) {
                text.append(POINT_SEPARATOR);
            }
            text.append(String.format(Locale.ROOT, "%.3f%s%.3f", p.x(), AXIS_SEPARATOR, p.y()));
        }
        return text.toString();
    }

    /** encode의 역. null·빈 문자열은 빈 모양이다. */
    public static RouteShape decode(String text) {
        if (text == null || text.isBlank()) {
            return new RouteShape(List.of());
        }
        List<ShapePoint> points = new ArrayList<>();
        for (String pair : text.split(POINT_SEPARATOR)) {
            String[] xy = pair.split(AXIS_SEPARATOR);
            points.add(new ShapePoint(Double.parseDouble(xy[0]), Double.parseDouble(xy[1])));
        }
        return new RouteShape(points);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /**
     * 정규화한 점 하나.
     *
     * @param x 0~1(동쪽으로 커진다)
     * @param y 0~1(북쪽으로 커진다)
     */
    public record ShapePoint(double x, double y) {}
}
