package com.a207.runkk.watch.run

import com.a207.runkk.app.domain.run.GpsWarmUp
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.run.WatchGpsSession
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * watch.run의 포트 구현을 Hilt에 묶는다(3.1, D-385 R64): MeasurementHost ← WatchMeasurementService.Host,
 * WatchGateway ← LocalWatchGateway, LocationPermission ← WatchLocationPermission.
 * 대결 조정자의 GPS 예열(W55)은 하지 않는다(GpsWarmUp.NONE): 워치는 화면이 Health Services로 예열한다(W53, WatchGpsSession).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchRunBindings {
    @Binds
    abstract fun measurementHost(h: WatchMeasurementService.Host): MeasurementHost

    @Binds
    abstract fun gateway(g: LocalWatchGateway): WatchGateway

    @Binds
    abstract fun permission(p: WatchLocationPermission): LocationPermission

    @Binds
    abstract fun gpsSession(impl: HealthServicesGpsWarmUp): WatchGpsSession

    companion object {
        @Provides
        fun gpsWarmUp(): GpsWarmUp = GpsWarmUp.NONE
    }
}
