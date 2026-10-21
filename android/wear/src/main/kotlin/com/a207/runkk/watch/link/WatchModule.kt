package com.a207.runkk.watch.link

import com.a207.runkk.watch.heart.BodySensorPermission
import com.a207.runkk.watch.heart.ExerciseHeartRateSensor
import com.a207.runkk.watch.heart.ExerciseSession
import com.a207.runkk.watch.heart.HealthServicesExerciseSession
import com.a207.runkk.watch.heart.HeartRateSensor
import com.a207.runkk.watch.heart.SensorPermission
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 워치 포트 ↔ 구현 바인딩과 앱 범위(Hilt). watch.heart·watch.link의 구현을 묶으므로 두 패키지를 모두 아는
 * watch.link에 둔다(5.1.2 watch.link → watch.heart). 앱 범위는 [PhoneLink]·HeartRateCollector가 쓴다(전역 범위 금지).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchModule {
    @Binds
    abstract fun heartRateSensor(impl: ExerciseHeartRateSensor): HeartRateSensor

    @Binds
    abstract fun exerciseSession(impl: HealthServicesExerciseSession): ExerciseSession

    @Binds
    abstract fun sensorPermission(impl: BodySensorPermission): SensorPermission

    @Binds
    abstract fun wearDataLayer(impl: PlayServicesWearDataLayer): WearDataLayer

    companion object {
        @Provides
        @Singleton
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        fun clock(): Clock = Clock.systemUTC()
    }
}
