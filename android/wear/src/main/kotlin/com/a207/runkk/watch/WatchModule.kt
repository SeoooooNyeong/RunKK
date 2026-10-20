package com.a207.runkk.watch

import com.a207.runkk.watch.heart.BodySensorPermission
import com.a207.runkk.watch.heart.HealthServicesHeartRateSensor
import com.a207.runkk.watch.heart.HeartRateSensor
import com.a207.runkk.watch.heart.SensorPermission
import com.a207.runkk.watch.link.PlayServicesWearDataLayer
import com.a207.runkk.watch.link.WearDataLayer
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
 * 워치 조립(Hilt). 워치는 Gradle 모듈 하나라 포트 ↔ 구현 바인딩과 앱 범위를 조립 루트에 둔다.
 * 앱 범위는 [com.a207.runkk.watch.link.PhoneLink]·[com.a207.runkk.watch.heart.HeartRateCollector]가 쓴다(GlobalScope 금지).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchModule {
    @Binds
    abstract fun heartRateSensor(impl: HealthServicesHeartRateSensor): HeartRateSensor

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
