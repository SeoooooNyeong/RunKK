package com.a207.runkk.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 위도·경도 한 점(설계 6.1.1).
 *
 * @param latitude -90~90
 * @param longitude -180~180
 */
public record GeoPoint(double latitude, double longitude) {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final int MAX_DECIMALS = 6;

    public GeoPoint {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("위도 범위 밖: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("경도 범위 밖: " + longitude);
        }
    }

    /** 하버사인 거리(D-18). */
    public Distance distanceTo(GeoPoint other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double a =
                Math.pow(Math.sin(dLat / 2), 2)
                        + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Distance.ofMeters(Math.round(EARTH_RADIUS_METERS * c));
    }

    /** 소수 자릿수를 낮춘다(NFR-038은 2). */
    public GeoPoint coarsen(int decimals) {
        if (decimals < 0 || decimals > MAX_DECIMALS) {
            throw new IllegalArgumentException("자릿수는 0~6: " + decimals);
        }
        return new GeoPoint(round(latitude, decimals), round(longitude, decimals));
    }

    private static double round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }
}
