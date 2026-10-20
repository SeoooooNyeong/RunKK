package com.a207.runkk.watch.heart

import android.content.Context
import android.os.SystemClock
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.SampleDataPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await

/**
 * Health Services 운동 세션(ExerciseClient)으로 심박을 잰다(13.2 워치). 화면이 꺼지거나 앱이 뒤로 가도
 * 운동 세션이 이어져 러닝 중 측정이 끊기지 않는다. 위치·거리는 재지 않는다(측정·기록은 폰, C-002).
 * 흐름을 모으면 세션을 열고, 취소하면 끝낸다.
 */
class HealthServicesHeartRateSensor @Inject constructor(@ApplicationContext context: Context) : HeartRateSensor {
    private val exerciseClient = HealthServices.getClient(context).exerciseClient

    override fun heartRates(): Flow<WatchHeartRate> = callbackFlow {
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        val callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                update.latestMetrics.getData(DataType.HEART_RATE_BPM).forEach {
                    trySend(it.toWatchHeartRate(bootInstant))
                }
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                close(throwable)
            }

            override fun onRegistered() = Unit

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit
        }
        exerciseClient.setUpdateCallback(callback)
        val config = ExerciseConfig.builder(ExerciseType.RUNNING)
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(false)
            .build()
        exerciseClient.startExerciseAsync(config).await()
        awaitClose {
            exerciseClient.endExerciseAsync()
            exerciseClient.clearUpdateCallbackAsync(callback)
        }
    }

    private fun SampleDataPoint<Double>.toWatchHeartRate(bootInstant: Instant): WatchHeartRate {
        val sensorStatus = (accuracy as? HeartRateAccuracy)?.sensorStatus
        val reliable = sensorStatus != HeartRateAccuracy.SensorStatus.NO_CONTACT &&
            sensorStatus != HeartRateAccuracy.SensorStatus.UNRELIABLE
        return classifyHeartRate(getTimeInstant(bootInstant).toEpochMilli(), value, reliable)
    }
}

/** 센서 값 하나를 심박 한 건으로 바꾼다. 신뢰할 수 없거나 0 이하이면 실패 측정(FR-041 "측정 상태 실패"). */
internal fun classifyHeartRate(measuredAtEpochMillis: Long, bpm: Double, reliable: Boolean): WatchHeartRate {
    val rounded = if (bpm.isFinite()) bpm.roundToInt() else 0
    return if (reliable && rounded > 0) {
        WatchHeartRate(measuredAtEpochMillis, rounded, WatchHeartRate.Status.OK)
    } else {
        WatchHeartRate(measuredAtEpochMillis, 0, WatchHeartRate.Status.FAILED)
    }
}
