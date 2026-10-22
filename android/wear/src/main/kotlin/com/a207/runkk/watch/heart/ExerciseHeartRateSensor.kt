package com.a207.runkk.watch.heart

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 운동 세션([ExerciseSession])으로 심박을 잰다(13.2 워치). 화면이 꺼지거나 앱이 뒤로 가도 세션이 이어져 러닝 중 측정이 끊기지 않는다.
 *
 * 세션은 잠금 하나로 한 번에 하나만 연다. 잠금을 쥔 동안 이 프로세스의 세션은 하나도 돌지 않으므로, 그때 보이는
 * "이 앱의 세션"은 프로세스가 죽어 남은 세션이다. 그런 세션은 새로 열기 전과 앱 시작 때 끝낸다(NFR-023).
 * 다른 앱의 세션은 끝내지도 덮어쓰지도 않는다.
 */
@Singleton
class ExerciseHeartRateSensor @Inject constructor(private val session: ExerciseSession) : HeartRateSensor {
    private val sessionMutex = Mutex()

    override fun heartRates(): Flow<WatchHeartRate> = flow {
        sessionMutex.withLock {
            when (session.owner()) {
                ExerciseOwner.OTHER_APP -> throw ExerciseInUseException()
                ExerciseOwner.THIS_APP -> session.end()
                ExerciseOwner.NONE -> Unit
            }
            // 세션 흐름은 끝낼 때 endExercise를 마친 뒤 돌아온다. 그 뒤에야 잠금을 풀어 다음 세션을 연다.
            emitAll(session.heartRates())
        }
    }

    override suspend fun endLeftoverSession(): Boolean {
        // 잠금을 못 잡으면 이 프로세스가 재는 중이다. 그 세션은 열기 전에 남은 세션을 이미 정리했다.
        if (!sessionMutex.tryLock()) return false
        try {
            if (session.owner() != ExerciseOwner.THIS_APP) return false
            session.end()
            return true
        } finally {
            sessionMutex.unlock()
        }
    }
}
