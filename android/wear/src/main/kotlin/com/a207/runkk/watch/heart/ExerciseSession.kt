package com.a207.runkk.watch.heart

import kotlinx.coroutines.flow.Flow

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
