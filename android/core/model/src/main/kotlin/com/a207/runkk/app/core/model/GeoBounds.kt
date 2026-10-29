package com.a207.runkk.app.core.model

/** 위도·경도 범위(RouteLine.bounds). RouteCanvas가 이 범위를 화면에 맞춘다. */
data class GeoBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double
)
