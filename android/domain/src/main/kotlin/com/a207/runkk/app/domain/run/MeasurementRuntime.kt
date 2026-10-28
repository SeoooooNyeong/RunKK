package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** 측정 실행 환경(권한·포그라운드 서비스·시계)을 다룬다(RunTracker 협력 객체, D-95). */
class MeasurementRuntime
@Inject
constructor(
    private val locationPermission: LocationPermission,
    private val measurementHost: MeasurementHost,
    private val settings: SettingsRepository,
    private val clock: Clock
) {
    /** OS 위치 권한과 서버 위치 동의(FR-031, NFR-022). 하나라도 없으면 Rejected(LOCATION_CONSENT). */
    fun checkStartable(): Outcome<Unit> = if (locationPermission.granted() && settings.locationConsented()) {
        Outcome.Success(Unit)
    } else {
        Outcome.Failure(AppError.Rejected(AppError.Rejected.LOCATION_CONSENT))
    }

    /** 포그라운드 측정 서비스 시작. OS가 거부하면 Failure. */
    fun startForeground(): Outcome<Unit> = measurementHost.startForeground()

    fun stop() {
        measurementHost.stop()
    }

    fun now(): Instant = clock.instant()
}
