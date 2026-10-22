package com.a207.runkk.watch.heart

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.SampleDataPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/**
 * Health Services ExerciseClient로 [ExerciseSession]을 구현한다(13.2 워치). 위치·거리는 재지 않는다(측정·기록은 폰, C-002).
 * 세션을 하나씩 여는 규칙과 남은 세션 정리는 [ExerciseHeartRateSensor]가 맡는다.
 */
class HealthServicesExerciseSession @Inject constructor(@ApplicationContext context: Context) : ExerciseSession {
    private val exerciseClient = HealthServices.getClient(context).exerciseClient

    override suspend fun owner(): ExerciseOwner =
        exerciseOwnerOf(exerciseClient.getCurrentExerciseInfoAsync().await().exerciseTrackedStatus)

    override suspend fun end() {
        exerciseClient.endExerciseAsync().await()
    }

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
        try {
            exerciseClient.setUpdateCallback(callback)
            val config = ExerciseConfig.builder(ExerciseType.RUNNING)
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .setIsAutoPauseAndResumeEnabled(false)
                .setIsGpsEnabled(false)
                .build()
            exerciseClient.startExerciseAsync(config).await()
            awaitClose()
        } finally {
            // 시작 도중 실패·취소에도 세션과 콜백을 정리한다. 정리가 끝나야 흐름이 끝나고 다음 세션이 열린다.
            withContext(NonCancellable) {
                runCatching { exerciseClient.endExerciseAsync().await() }
                runCatching { exerciseClient.clearUpdateCallbackAsync(callback).await() }
            }
        }
    }

    private fun SampleDataPoint<Double>.toWatchHeartRate(bootInstant: Instant): WatchHeartRate {
        val sensorStatus = (accuracy as? HeartRateAccuracy)?.sensorStatus
        val isReliable = sensorStatus != HeartRateAccuracy.SensorStatus.NO_CONTACT &&
            sensorStatus != HeartRateAccuracy.SensorStatus.UNRELIABLE
        return classifyHeartRate(getTimeInstant(bootInstant).toEpochMilli(), value, isReliable)
    }
}

/**
 * ExerciseTrackedStatus → 세션 주인. 알 수 없음(UNKNOWN)은 남은 세션으로 보지 않는다.
 * 1.1.0은 IntDef 애너테이션 자체를 라이브러리 전용으로 표시해 상수 접근까지 RestrictedApi로 잡는다.
 * 상수는 ExerciseInfo.exerciseTrackedStatus의 공개 값이라 이 함수에서만 막는다.
 */
@SuppressLint("RestrictedApi")
internal fun exerciseOwnerOf(trackedStatus: Int): ExerciseOwner = when (trackedStatus) {
    ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS -> ExerciseOwner.THIS_APP
    ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS -> ExerciseOwner.OTHER_APP
    else -> ExerciseOwner.NONE
}

/** 센서 값 하나를 심박 한 건으로 바꾼다. 신뢰할 수 없거나 0 이하이면 실패 측정(FR-041 "측정 상태 실패"). */
internal fun classifyHeartRate(measuredAtEpochMillis: Long, bpm: Double, isReliable: Boolean): WatchHeartRate {
    val rounded = if (bpm.isFinite()) bpm.roundToInt() else 0
    return if (isReliable && rounded > 0) {
        WatchHeartRate(measuredAtEpochMillis, rounded, WatchHeartRate.Status.OK)
    } else {
        WatchHeartRate(measuredAtEpochMillis, 0, WatchHeartRate.Status.FAILED)
    }
}
