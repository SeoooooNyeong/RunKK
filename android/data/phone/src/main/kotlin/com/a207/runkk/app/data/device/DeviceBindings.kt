package com.a207.runkk.app.data.device

import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * GuidanceOutput ← AndroidGuidanceOutput, LocationPermission ← AndroidLocationPermission,
 * MeasurementHost ← MeasurementService.Host(6.1.14, D-61).
 * 측정 시각(RunTracker·두 조정자가 주입받는 java.time.Clock)도 기기 기능이라 여기서 하나만 내준다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceBindings {
    @Binds
    abstract fun guidanceOutput(impl: AndroidGuidanceOutput): GuidanceOutput

    @Binds
    abstract fun locationPermission(impl: AndroidLocationPermission): LocationPermission

    @Binds
    abstract fun measurementHost(impl: MeasurementService.Host): MeasurementHost

    companion object {
        @Provides
        fun clock(): Clock = Clock.systemUTC()
    }
}
