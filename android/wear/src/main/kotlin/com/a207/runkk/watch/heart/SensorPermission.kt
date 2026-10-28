package com.a207.runkk.watch.heart

import kotlinx.coroutines.flow.Flow

/** OS 심박 권한 포트(BODY_SENSORS, API 36부터 health.READ_HEART_RATE). 구현 [BodySensorPermission]. */
interface SensorPermission {
    /** 이 기기에서 요청할 권한 이름. */
    val name: String

    fun granted(): Boolean
}
