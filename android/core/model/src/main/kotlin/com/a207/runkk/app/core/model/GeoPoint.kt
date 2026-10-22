package com.a207.runkk.app.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** 위도·경도 한 점(서버 common.GeoPoint와 같은 규칙, 6.1.1, D-18 하버사인). */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "위도 범위 밖: $latitude" }
        require(longitude in -180.0..180.0) { "경도 범위 밖: $longitude" }
    }

    /** 하버사인 거리(미터 단위로 반올림). */
    fun distanceTo(other: GeoPoint): Distance = Distance(metersTo(other).roundToLong())

    /**
     * 하버사인 거리(소수 미터). 1초 간격 포인트 사이 거리(수 m)를 Long으로 잘라 누적하면 오차가 커지므로
     * RunSession이 누적에 쓴다(A-02).
     */
    fun metersTo(other: GeoPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.longitude - longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** 소수 자릿수 낮추기(NFR-038은 2). */
    fun coarsen(decimals: Int): GeoPoint {
        require(decimals in 0..6) { "decimals는 0~6: $decimals" }
        return GeoPoint(round(latitude, decimals), round(longitude, decimals))
    }

    private fun round(value: Double, decimals: Int): Double =
        BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
