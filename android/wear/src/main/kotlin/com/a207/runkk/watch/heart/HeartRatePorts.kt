package com.a207.runkk.watch.heart

import kotlinx.coroutines.flow.Flow

/** 심박 센서 포트. 구현은 Health Services 운동 세션을 하나씩 여는 [ExerciseHeartRateSensor]. 구독하는 동안만 잰다. */
interface HeartRateSensor {
    /** 차가운 흐름: 모으기 시작하면 측정을 켜고, 취소하면 끈다. 센서를 쓸 수 없으면 예외로 끝난다. */
    fun heartRates(): Flow<WatchHeartRate>

    /**
     * 이 앱이 열었다가 프로세스가 죽어 남은 운동 세션을 끝낸다(앱 시작 때, NFR-023). 다른 앱의 세션은 건드리지 않고,
     * 이 프로세스가 지금 재는 중이면 아무것도 하지 않는다. 남은 세션을 끝냈으면 참.
     */
    suspend fun endLeftoverSession(): Boolean
}

/** OS 심박 권한 포트(BODY_SENSORS, API 36부터 health.READ_HEART_RATE). 구현 [BodySensorPermission]. */
interface SensorPermission {
    /** 이 기기에서 요청할 권한 이름. */
    val name: String

    fun granted(): Boolean
}

/**
 * Health Services 운동 세션 포트(ExerciseClient). 구현 [HealthServicesExerciseSession].
 * 기기에는 운동 세션이 하나만 돌고, 앱 프로세스가 죽어도 세션은 남는다. 그래서 세션의 주인을 묻고 끝낼 수 있어야 한다.
 */
interface ExerciseSession {
    /** 지금 기기에서 도는 운동 세션의 주인. */
    suspend fun owner(): ExerciseOwner

    /** 이 앱이 연 운동 세션을 끝낸다. */
    suspend fun end()

    /** 세션을 열어 심박을 낸다. 취소·오류로 끝나면 세션을 끝내고 콜백을 푼 뒤에야 끝난다. */
    fun heartRates(): Flow<WatchHeartRate>
}

/** 운동 세션의 주인(ExerciseTrackedStatus). 알 수 없음은 [NONE]으로 본다. */
enum class ExerciseOwner {
    NONE,
    THIS_APP,
    OTHER_APP
}

/** 다른 앱(삼성 헬스 등)이 운동 세션을 쓰는 중이라 열지 않았다. 열면 그 앱의 운동이 끝나 버린다. */
class ExerciseInUseException : IllegalStateException("다른 앱이 운동 세션을 쓰는 중")
