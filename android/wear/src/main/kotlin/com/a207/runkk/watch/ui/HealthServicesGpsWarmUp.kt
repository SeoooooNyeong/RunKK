package com.a207.runkk.watch.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAvailability
import androidx.health.services.client.data.WarmUpConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Health Services 운동 준비(prepareExercise, ExerciseType.RUNNING, LOCATION)로 GPS를 예열한다(W53). 위치 가용성
 * (onAvailabilityChanged)을 찾는 중·준비됨으로 바꿔 낸다. 심박은 예열하지 않는다(심박 동의 전 센서를 켜지 않는다, NFR-023).
 *
 * - 운동 세션이 이미 있으면(다른 앱 운동, 이 앱 측정·동행 심박) 예열하지 않는다(열면 그 운동과 다툰다).
 * - 모으기를 멈추면 준비를 끝내고 콜백을 푼다. 예열이 측정 운동(WatchExerciseSource.start)으로 이어졌으면(콜백이 준비 아닌 상태를
 *   받음) 끝내지 않는다. 화면은 카운트다운 전에 모으기를 멈추므로 보통 측정 운동보다 먼저 끝난다.
 * - 한 번에 하나만 예열하고, [WARM_UP_LIMIT_MILLIS]가 지나면 스스로 끝낸다.
 */
@Singleton
class HealthServicesGpsWarmUp internal constructor(
    private val exerciseClient: ExerciseClient,
    private val isLocationPermitted: () -> Boolean
) : GpsWarmUp {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        HealthServices.getClient(context).exerciseClient,
        isLocationPermitted = {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    )

    private val lock = Mutex()

    override fun readiness(): Flow<GpsWarmUp.Readiness> = channelFlow {
        lock.withLock {
            if (!isLocationPermitted() || !isExerciseFree()) return@withLock
            val callback = WarmUpCallback { trySend(it) }
            try {
                exerciseClient.setUpdateCallback(callback)
                exerciseClient.prepareExerciseAsync(
                    WarmUpConfig(ExerciseType.RUNNING, setOf(DataType.LOCATION))
                ).await()
                if (callback.latest == null) send(GpsWarmUp.Readiness.SEARCHING)
                withTimeoutOrNull(WARM_UP_LIMIT_MILLIS) { awaitCancellation() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "GPS 예열 실패")
            } finally {
                withContext(NonCancellable) {
                    if (!callback.isTakenOver) attempt { exerciseClient.endExerciseAsync().await() }
                    attempt { exerciseClient.clearUpdateCallbackAsync(callback).await() }
                }
            }
        }
    }.distinctUntilChanged()

    private suspend fun isExerciseFree(): Boolean = try {
        isNoExercise(exerciseClient.getCurrentExerciseInfoAsync().await().exerciseTrackedStatus)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "운동 세션 상태를 읽지 못했다")
        false
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "GPS 예열 정리 실패")
        }
    }

    /** 예열 콜백. 위치 가용성만 본다. 준비 아닌 운동 상태를 받으면 예열이 측정 운동으로 이어진 것이다. */
    internal class WarmUpCallback(private val onReadiness: (GpsWarmUp.Readiness) -> Unit) : ExerciseUpdateCallback {
        @Volatile
        var latest: GpsWarmUp.Readiness? = null
            private set

        @Volatile
        var isTakenOver = false
            private set

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) = onState(update.exerciseStateInfo.state)

        fun onState(state: ExerciseState) {
            if (state != ExerciseState.PREPARING) isTakenOver = true
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            if (dataType != DataType.LOCATION || availability !is LocationAvailability) return
            val readiness = when (availability) {
                LocationAvailability.ACQUIRED_TETHERED, LocationAvailability.ACQUIRED_UNTETHERED ->
                    GpsWarmUp.Readiness.READY

                else -> GpsWarmUp.Readiness.SEARCHING
            }
            latest = readiness
            onReadiness(readiness)
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            Timber.w(throwable, "GPS 예열 콜백 등록 실패")
        }
    }

    companion object {
        /** 예열 상한(5분). 화면이 켜진 채 시작하지 않으면 GPS를 끈다. */
        const val WARM_UP_LIMIT_MILLIS = 5 * 60_000L

        /** 1.1.0은 ExerciseTrackedStatus 상수까지 RestrictedApi로 잡는다(watch.run·watch.heart와 같다). */
        @SuppressLint("RestrictedApi")
        private fun isNoExercise(trackedStatus: Int): Boolean =
            trackedStatus != ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS &&
                trackedStatus != ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS
    }
}
