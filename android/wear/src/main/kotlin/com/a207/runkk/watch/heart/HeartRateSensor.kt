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
