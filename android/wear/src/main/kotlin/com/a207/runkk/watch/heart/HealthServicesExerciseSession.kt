package com.a207.runkk.watch.heart

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/**
 * Health Services ExerciseClient로 [ExerciseSession]을 구현한다(13.2 워치). 위치·거리는 재지 않는다(측정·기록은 폰, C-002).
 * 세션을 하나씩 여는 규칙과 남은 세션 정리는 [ExerciseHeartRateSensor]가 맡는다.
 * 신체 활동 권한이 있고 기기가 지원하면 손목 걸음(STEPS_PER_MINUTE, 없으면 STEPS 누적)도 함께 재 [steps]로 낸다(S15P31A207-242).
 * 권한이 없거나 지원하지 않거나 능력 조회가 실패하면 심박만 잰다.
 */
class HealthServicesExerciseSession @Inject constructor(@ApplicationContext private val context: Context) :
    ExerciseSession {
    private val exerciseClient = HealthServices.getClient(context).exerciseClient
    private val stepSamples = MutableSharedFlow<WatchSteps>(extraBufferCapacity = STEP_BUFFER)

    override val steps: Flow<WatchSteps> get() = stepSamples.asSharedFlow()

    override suspend fun owner(): ExerciseOwner =
        exerciseOwnerOf(exerciseClient.getCurrentExerciseInfoAsync().await().exerciseTrackedStatus)

    override suspend fun end() {
        exerciseClient.endExerciseAsync().await()
    }

    override fun heartRates(): Flow<WatchHeartRate> = callbackFlow {
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        var stepTotal = 0L
        val callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                val metrics = update.latestMetrics
                metrics.getData(DataType.HEART_RATE_BPM).forEach {
                    trySend(it.toWatchHeartRate(bootInstant))
                }
                metrics.getData(DataType.STEPS_PER_MINUTE).forEach {
                    stepSamples.tryEmit(WatchSteps(it.getTimeInstant(bootInstant).toEpochMilli(), perMinute = it.value))
                }
                metrics.getData(DataType.STEPS).forEach {
                    if (it.value >= 0) stepTotal += it.value
                    stepSamples.tryEmit(WatchSteps(it.getEndInstant(bootInstant).toEpochMilli(), total = stepTotal))
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
            val stepTypes = stepDataTypes()
            exerciseClient.setUpdateCallback(callback)
            val config = ExerciseConfig.builder(ExerciseType.RUNNING)
                .setDataTypes(setOf<DataType<*, *>>(DataType.HEART_RATE_BPM) + stepTypes)
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

    /** 신체 활동 권한이 있으면 기기가 지원하는 걸음 종류(STEPS_PER_MINUTE, 없으면 STEPS). 없거나 조회가 실패하면 빈 집합. */
    private suspend fun stepDataTypes(): Set<DataType<*, *>> {
        val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted) return emptySet()
        val supported = try {
            exerciseClient.getCapabilitiesAsync().await()
                .getExerciseTypeCapabilities(ExerciseType.RUNNING).supportedDataTypes
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return emptySet()
        }
        return stepDataTypesOf(supported)
    }

    private fun SampleDataPoint<Double>.toWatchHeartRate(bootInstant: Instant): WatchHeartRate {
        val sensorStatus = (accuracy as? HeartRateAccuracy)?.sensorStatus
        val isReliable = sensorStatus != HeartRateAccuracy.SensorStatus.NO_CONTACT &&
            sensorStatus != HeartRateAccuracy.SensorStatus.UNRELIABLE
        return classifyHeartRate(getTimeInstant(bootInstant).toEpochMilli(), value, isReliable)
    }
}

/** 지원 목록에서 동행 세션에 넣을 걸음 종류: STEPS_PER_MINUTE, 없으면 STEPS, 둘 다 없으면 빈 집합. */
internal fun stepDataTypesOf(supported: Set<DataType<*, *>>): Set<DataType<*, *>> = when {
    DataType.STEPS_PER_MINUTE in supported -> setOf(DataType.STEPS_PER_MINUTE)
    DataType.STEPS in supported -> setOf(DataType.STEPS)
    else -> emptySet()
}

private const val STEP_BUFFER = 64

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
