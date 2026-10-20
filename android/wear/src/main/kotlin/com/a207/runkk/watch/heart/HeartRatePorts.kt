package com.a207.runkk.watch.heart

import kotlinx.coroutines.flow.Flow

/** 심박 센서 포트. 구현은 Health Services 운동 세션([HealthServicesHeartRateSensor]). 구독하는 동안만 잰다. */
interface HeartRateSensor {
    /** 차가운 흐름: 모으기 시작하면 측정을 켜고, 취소하면 끈다. 센서를 쓸 수 없으면 예외로 끝난다. */
    fun heartRates(): Flow<WatchHeartRate>
}

/** OS 심박 권한 포트(BODY_SENSORS, API 36부터 health.READ_HEART_RATE). 구현 [BodySensorPermission]. */
interface SensorPermission {
    fun granted(): Boolean
}
