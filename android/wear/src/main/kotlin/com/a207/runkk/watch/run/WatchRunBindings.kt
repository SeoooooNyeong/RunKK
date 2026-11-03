package com.a207.runkk.watch.run

import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.WatchGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * watch.run의 포트 구현을 Hilt에 묶는다(3.1, D-385 R64): MeasurementHost ← WatchMeasurementService.Host,
 * WatchGateway ← LocalWatchGateway, LocationPermission ← WatchLocationPermission.
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
}
