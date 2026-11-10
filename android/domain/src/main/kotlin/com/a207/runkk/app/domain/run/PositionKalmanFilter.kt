package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.GeoPoint
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos

/** 국소 미터 좌표에서 동·북 방향별 위치와 속도를 추정한다. 두 축의 공분산은 같다. */
internal class PositionKalmanFilter {
    private lateinit var origin: GeoPoint
    private lateinit var updatedAt: Instant
    private var metersPerLongitudeDegree = METERS_PER_DEGREE
    private var east = 0.0
    private var north = 0.0
    private var eastSpeed = 0.0
    private var northSpeed = 0.0
    private var positionVariance = 0.0
    private var positionSpeedCovariance = 0.0
    private var speedVariance = INITIAL_SPEED_VARIANCE

    fun reset(sample: LocationSample): GeoPoint {
        origin = sample.point
        updatedAt = sample.at
        metersPerLongitudeDegree = METERS_PER_DEGREE * cos(Math.toRadians(origin.latitude)).coerceAtLeast(0.000001)
        east = 0.0
        north = 0.0
        eastSpeed = 0.0
        northSpeed = 0.0
        positionVariance = measurementVariance(sample)
        positionSpeedCovariance = 0.0
        speedVariance = INITIAL_SPEED_VARIANCE
        return origin
    }

    /** 예측 오차가 큰 표본은 거절한다. 거절된 표본은 추정 상태나 기준 시각을 바꾸지 않는다. */
    fun update(sample: LocationSample): GeoPoint? {
        val dt = Duration.between(updatedAt, sample.at).toMillis() / 1_000.0
        val dt2 = dt * dt
        val predictedEast = east + dt * eastSpeed
        val predictedNorth = north + dt * northSpeed
        val predictedPositionVariance = positionVariance + 2 * dt * positionSpeedCovariance + dt2 * speedVariance +
            dt2 * dt2 * ACCEL_VARIANCE / 4
        val predictedCovariance = positionSpeedCovariance + dt * speedVariance + dt2 * dt * ACCEL_VARIANCE / 2
        val predictedSpeedVariance = speedVariance + dt2 * ACCEL_VARIANCE

        val measuredEast = wrapLongitude(sample.point.longitude - origin.longitude) * metersPerLongitudeDegree
        val measuredNorth = (sample.point.latitude - origin.latitude) * METERS_PER_DEGREE
        val eastError = measuredEast - predictedEast
        val northError = measuredNorth - predictedNorth
        val variance = predictedPositionVariance + measurementVariance(sample)
        if (eastError * eastError + northError * northError > INNOVATION_LIMIT * variance) return null

        val positionGain = predictedPositionVariance / variance
        val speedGain = predictedCovariance / variance
        east = predictedEast + positionGain * eastError
        north = predictedNorth + positionGain * northError
        eastSpeed += speedGain * eastError
        northSpeed += speedGain * northError
        positionVariance = (1 - positionGain) * predictedPositionVariance
        positionSpeedCovariance = (1 - positionGain) * predictedCovariance
        speedVariance = predictedSpeedVariance - speedGain * predictedCovariance
        updatedAt = sample.at
        return GeoPoint(
            (origin.latitude + north / METERS_PER_DEGREE).coerceIn(-90.0, 90.0),
            wrapLongitude(origin.longitude + east / metersPerLongitudeDegree)
        )
    }

    private fun measurementVariance(sample: LocationSample): Double {
        val accuracy = sample.accuracyMeters.coerceAtLeast(1.0)
        return accuracy * accuracy
    }

    private fun wrapLongitude(degrees: Double): Double = ((degrees + 180) % 360 + 360) % 360 - 180

    private companion object {
        const val METERS_PER_DEGREE = 6_371_000.0 * PI / 180
        const val INITIAL_SPEED_VARIANCE = 9.0

        // shortcut: 초기 러닝 튜닝 값이며, 실제 보행·회전 경로로 지연과 거리 오차를 조정한다.
        const val ACCEL_VARIANCE = 4.0
        const val INNOVATION_LIMIT = 13.82 // 자유도 2인 카이제곱 분포의 99.9% 분위수.
    }
}
