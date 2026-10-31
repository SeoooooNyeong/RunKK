package com.a207.runkk.watch.run

import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.watch.heart.ExerciseHeartRateSensor
import com.a207.runkk.watch.heart.HeartRateSensor
import dagger.Binds
import dagger.Module

@Module
abstract class CrossPackageBindsFixture {
    @Binds
    abstract fun gateway(impl: LocalWatchGateway): WatchGateway

    @Binds
    abstract fun host(impl: WatchMeasurementService.Host): MeasurementHost

    @Binds
    abstract fun sensor(impl: ExerciseHeartRateSensor): HeartRateSensor
}
