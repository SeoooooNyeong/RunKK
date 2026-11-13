package com.a207.runkk.watch.run

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.a207.runkk.app.domain.run.WatchGpsSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** 화면이 보일 때 운동과 같은 Health Services 콜백으로 GPS를 준비한다. */
@Singleton
class HealthServicesGpsWarmUp internal constructor(
    private val source: WatchExerciseSource,
    private val isLocationPermitted: () -> Boolean
) : WatchGpsSession {
    @Inject
    constructor(@ApplicationContext context: Context, source: WatchExerciseSource) : this(
        source,
        isLocationPermitted = {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    )

    override fun readiness(): Flow<WatchGpsSession.Readiness> = channelFlow {
        if (!isLocationPermitted()) return@channelFlow
        try {
            withTimeoutOrNull(WARM_UP_LIMIT_MILLIS) {
                source.gpsReadiness().collect { ready ->
                    send(if (ready) WatchGpsSession.Readiness.READY else WatchGpsSession.Readiness.SEARCHING)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "GPS 예열 실패")
        }
    }

    override suspend fun finishMeasurement(at: Instant) = source.finish(at)

    companion object {
        /** 화면이 켜진 채 시작하지 않으면 GPS를 끈다. */
        const val WARM_UP_LIMIT_MILLIS = 5 * 60_000L
    }
}
