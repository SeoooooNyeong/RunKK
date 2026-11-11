package com.a207.runkk.watch.run

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.LocationAccuracy
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.CadenceMeter
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.MotionSample
import com.a207.runkk.app.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Health Services 운동 세션 하나(ExerciseType.RUNNING, LOCATION + HEART_RATE_BPM)에서 위치와 심박을 받아 domain 표본으로 바꾼다
 * (3.1, D-367). 거리는 Health Services DISTANCE를 쓰지 않고 domain(RunSession·LocationFilter)이 위치로 계산한다(폰과 같은 규칙).
 *
 * - 심박은 심박 동의·워치연동·OS 심박 권한이 모두 있을 때만 세션에 넣는다(센서를 켜지 않는다, NFR-023). 믿을 수 없는 값은 버린다.
 * - 다른 앱(삼성 헬스 등)이 운동 중이면 열지 않는다(열면 그 앱의 운동이 끝난다). 이 앱이 남긴 세션은 끝내고 연다.
 * - [pause]·[resume]: 자유런 일시정지 때 운동 세션도 멈춰 GPS를 끈다(NFR-048).
 * - 속도·케이던스(S15P31A207-242): 신체 활동 권한(ACTIVITY_RECOGNITION)이 있고 기기가 지원하면(capabilities) SPEED와
 *   STEPS_PER_MINUTE를 세션에 넣어 [motions]로 낸다. STEPS_PER_MINUTE가 없고 STEPS만 있으면 걸음 수로 셈한다(CadenceMeter).
 *   권한이 없거나 지원하지 않으면 넣지 않는다(서버가 속도를 위치로 셈하고 케이던스는 비운다).
 * 동행 모드 심박 수집(watch.heart)과 같은 운동 세션을 다투지 않는다: 단독 측정 중에는 폰 측정이 막혀 있다(D-373 (3)).
 */
@Singleton
class WatchExerciseSource internal constructor(
    private val exerciseClient: ExerciseClient,
    private val clock: Clock,
    private val isHeartRateAllowed: () -> Boolean,
    private val elapsedRealtimeMillis: () -> Long,
    private val isMotionAllowed: () -> Boolean = { false }
) {
    @Inject
    constructor(@ApplicationContext context: Context, clock: Clock, settings: SettingsRepository) : this(
        HealthServices.getClient(context).exerciseClient,
        clock,
        isHeartRateAllowed = {
            settings.heartRateConsented() && settings.watchLinked() && heartRatePermitted(context)
        },
        elapsedRealtimeMillis = SystemClock::elapsedRealtime,
        isMotionAllowed = { activityRecognitionPermitted(context) }
    )

    private val locationSamples = MutableSharedFlow<LocationSample>(extraBufferCapacity = SAMPLE_BUFFER)
    private val heartRateSamples = MutableSharedFlow<HeartRateSample>(extraBufferCapacity = SAMPLE_BUFFER)
    private val motionSamples = MutableSharedFlow<MotionSample>(extraBufferCapacity = SAMPLE_BUFFER)

    /** STEPS로 케이던스를 셈할 때 쓰는 누적 걸음 수와 계산기. 세션마다 새로 만든다. 콜백 스레드 하나에서만 쓴다. */
    @Volatile
    private var stepCadence = StepCadence()
    private val endedSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * 이 앱이 끝내지 않았는데 운동 세션이 끝났다(다른 앱이 운동을 시작함, 시스템이 끝냄). 측정 서비스가 받아 측정을 끝낸다
     * (위치 없이 시간만 흐르지 않게).
     */
    internal val ended: Flow<Unit> = endedSignals.asSharedFlow()

    /** 운동 세션 시각(부팅 뒤 경과)을 벽시계로 바꾸는 기준. [start] 때 정한다. */
    @Volatile
    private var bootInstant: Instant = Instant.EPOCH

    private val receiver = MetricsReceiver()

    /**
     * 열기·끝내기를 한 줄로 세운다(남은 위험 M2, 코드 리뷰 WR12-9). 끝내기가 끝나기 전에 새 세션을 열면 늦게 끝난 끝내기가 새 세션의
     * 콜백을 풀어 버린다.
     */
    private val sessionLock = Mutex()

    /** 이 앱이 연 세션이 열려 있다. 끝낸 뒤 늦게 온 운동 끝 알림은 [ended]로 보내지 않는다. */
    @Volatile
    private var isOpen = false

    /**
     * 이번 세션의 시작을 요청한 시각(벽시계). 이보다 먼저 시작한 세션의 끝 알림은 이전 세션(이 앱이 남긴 세션을 [open]이 끝낸 것,
     * 동행 심박 수집, 지난 측정)의 것이라 [ended]로 보내지 않는다. Health Services는 끝 알림을 새 콜백에 늦게 주기도 한다.
     */
    @Volatile
    private var requestedAt: Instant = Instant.MAX

    /** 세션을 연다. 다른 앱이 운동 중이면 Failure(Rejected(EXERCISE_IN_USE)), Health Services 오류는 Failure(Rejected(EXERCISE_FAILED)). */
    suspend fun start(): Outcome<Unit> = sessionLock.withLock {
        try {
            open()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "운동 세션을 열지 못했다")
            Outcome.Failure(AppError.Rejected(EXERCISE_FAILED))
        }
    }

    private suspend fun open(): Outcome<Unit> {
        // 끝내지 않은 이 앱 세션을 아래에서 끝낼 때 오는 끝 알림을 새 측정의 끝으로 보내지 않는다.
        isOpen = false
        when (exerciseOwnerOf(exerciseClient.getCurrentExerciseInfoAsync().await().exerciseTrackedStatus)) {
            Owner.OTHER_APP -> return Outcome.Failure(AppError.Rejected(EXERCISE_IN_USE))
            Owner.THIS_APP -> exerciseClient.endExerciseAsync().await()
            Owner.NONE -> Unit
        }
        bootInstant = Instant.ofEpochMilli(clock.millis() - elapsedRealtimeMillis())
        requestedAt = clock.instant()
        stepCadence = StepCadence()
        val motionTypes = if (isMotionAllowed()) motionDataTypes() else emptySet()
        exerciseClient.setUpdateCallback(receiver)
        val dataTypes = buildSet<DataType<*, *>> {
            add(DataType.LOCATION)
            if (isHeartRateAllowed()) add(DataType.HEART_RATE_BPM)
            addAll(motionTypes)
        }
        val config = ExerciseConfig.builder(ExerciseType.RUNNING)
            .setDataTypes(dataTypes)
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(true)
            .build()
        try {
            exerciseClient.startExerciseAsync(config).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 시작이 실패하면 걸어 둔 콜백을 푼다(남은 콜백이 다음 세션 값을 받지 않게).
            attempt { exerciseClient.clearUpdateCallbackAsync(receiver).await() }
            throw e
        }
        isOpen = true
        return Outcome.Success(Unit)
    }

    /**
     * 기기가 지원하는 속도·케이던스 데이터 종류. SPEED, 그리고 STEPS_PER_MINUTE(없으면 STEPS). 지원 목록을 읽지 못하면 비운다(세션은
     * 위치·심박만으로 연다).
     */
    private suspend fun motionDataTypes(): Set<DataType<*, *>> {
        val supported = try {
            exerciseClient.getCapabilitiesAsync().await()
                .getExerciseTypeCapabilities(ExerciseType.RUNNING).supportedDataTypes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "운동 능력을 읽지 못해 속도·케이던스 없이 연다")
            return emptySet()
        }
        return motionDataTypesOf(supported)
    }

    /** 위치 표본(측정일시, 좌표, 수평 정확도 반경 m). 정확도를 모르면 Double.MAX_VALUE(domain LocationFilter가 거른다). */
    fun locations(): Flow<LocationSample> = locationSamples.asSharedFlow()

    /** 믿을 수 있는 심박 표본. */
    fun heartRates(): Flow<HeartRateSample> = heartRateSamples.asSharedFlow()

    /** 기기가 잰 속도·케이던스 표본(S15P31A207-242). 세션에 넣지 않았으면 아무것도 내지 않는다. */
    fun motions(): Flow<MotionSample> = motionSamples.asSharedFlow()

    suspend fun pause() {
        attempt { exerciseClient.pauseExerciseAsync().await() }
    }

    suspend fun resume() {
        attempt { exerciseClient.resumeExerciseAsync().await() }
    }

    /** 세션을 끝내고 콜백을 푼다. 실패해도 예외를 올리지 않는다(다음 [start]가 남은 세션을 끝낸다). */
    suspend fun end() = sessionLock.withLock {
        isOpen = false
        attempt { exerciseClient.endExerciseAsync().await() }
        attempt { exerciseClient.clearUpdateCallbackAsync(receiver).await() }
    }

    /** Health Services 호출 하나. 실패(서비스 없음·세션 없음 등)는 기록만 하고 삼킨다. 취소는 그대로 전한다. */
    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "운동 세션 호출 실패")
        }
    }

    /** 운동 세션 콜백. 측정값만 표본으로 바꾸고 나머지(랩·가용성)는 쓰지 않는다. */
    internal inner class MetricsReceiver : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            onMetrics(update.latestMetrics)
            val info = update.exerciseStateInfo
            onState(info.state.isEnded, update.startTime, info.endReason)
        }

        /**
         * 운동 세션 상태. 이 앱이 연 세션이 열려 있는데 그 세션이 끝났을 때만 알린다. 끝낸 뒤 늦게 온 알림과, 이번 시작 요청보다 먼저
         * 시작한 이전 세션의 끝 알림([startTime]이 [requestedAt] 전)은 버린다. 시작 시각이 없으면 이번 세션으로 본다.
         */
        fun onState(isEnded: Boolean, startTime: Instant?, endReason: Int) {
            if (!isEnded) return
            val isPrevious = startTime != null && startTime.isBefore(requestedAt.minus(START_SLACK))
            Timber.i("운동 세션 끝 알림: 사유 %d, 시작 %s, 열림 %b, 이전 세션 %b", endReason, startTime, isOpen, isPrevious)
            if (isOpen && !isPrevious) endedSignals.tryEmit(Unit)
        }

        fun onMetrics(container: DataPointContainer) {
            val boot = bootInstant
            // 화면이 꺼져 위치가 묶여 와도 필터에는 측정 시각순으로 넘긴다.
            container.getData(DataType.LOCATION).sortedBy { it.getTimeInstant(boot) }.forEach { point ->
                val accuracy = (point.accuracy as? LocationAccuracy)?.horizontalPositionErrorMeters ?: Double.MAX_VALUE
                val location = point.value
                val geo = runCatching { GeoPoint(location.latitude, location.longitude) }.getOrNull() ?: return@forEach
                locationSamples.tryEmit(LocationSample(point.getTimeInstant(boot), geo, accuracy))
            }
            container.getData(DataType.HEART_RATE_BPM).forEach { point ->
                val status = (point.accuracy as? HeartRateAccuracy)?.sensorStatus
                val isReliable = status != HeartRateAccuracy.SensorStatus.NO_CONTACT &&
                    status != HeartRateAccuracy.SensorStatus.UNRELIABLE
                val bpm = if (point.value.isFinite()) point.value.roundToInt() else 0
                if (isReliable &&
                    bpm > 0
                ) {
                    heartRateSamples.tryEmit(HeartRateSample(point.getTimeInstant(bootInstant), bpm))
                }
            }
            onMotionMetrics(container)
        }

        /** 속도(m/s)·분당 걸음 수·걸음 수를 움직임 표본으로 바꾼다. 음수·무한대 값은 버린다(MotionSample.isUsable). */
        private fun onMotionMetrics(container: DataPointContainer) {
            container.getData(DataType.SPEED).forEach { point ->
                emitMotion(MotionSample(point.getTimeInstant(bootInstant), speedMps = point.value))
            }
            container.getData(DataType.STEPS_PER_MINUTE).forEach { point ->
                emitMotion(MotionSample(point.getTimeInstant(bootInstant), cadenceSpm = point.value.toDouble()))
            }
            container.getData(DataType.STEPS).forEach { point ->
                val at = point.getEndInstant(bootInstant)
                stepCadence.accept(at, point.value)?.let { emitMotion(MotionSample(at, cadenceSpm = it)) }
            }
        }

        private fun emitMotion(sample: MotionSample) {
            if (sample.isUsable) motionSamples.tryEmit(sample)
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            Timber.w(throwable, "운동 세션 콜백 등록 실패")
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit
    }

    /** STEPS(구간마다 늘어난 걸음 수)를 누적해 CadenceMeter에 넘긴다. */
    private class StepCadence {
        private val meter = CadenceMeter()
        private var total = 0L

        fun accept(at: Instant, delta: Long): Double? {
            if (delta < 0) return null
            total += delta
            return meter.accept(at, total)
        }
    }

    private enum class Owner {
        NONE,
        THIS_APP,
        OTHER_APP
    }

    companion object {
        /** 다른 앱이 운동 세션을 쓰는 중이라 열지 않았다(사유 코드). */
        const val EXERCISE_IN_USE = "EXERCISE_IN_USE"

        /** Health Services 호출이 실패했다(사유 코드). */
        const val EXERCISE_FAILED = "EXERCISE_FAILED"

        private const val SAMPLE_BUFFER = 64
        private const val API_BAKLAVA = 36

        /** 세션 시작 시각과 시작 요청 시각의 허용 오차. 이전 세션은 적어도 그만큼 먼저 시작했다. */
        private val START_SLACK: Duration = Duration.ofSeconds(1)
        private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

        /** 1.1.0은 ExerciseTrackedStatus 상수까지 RestrictedApi로 잡는다. 공개 값이라 이 함수에서만 막는다(watch.heart와 같다). */
        @SuppressLint("RestrictedApi")
        private fun exerciseOwnerOf(trackedStatus: Int): Owner = when (trackedStatus) {
            ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS -> Owner.THIS_APP
            ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS -> Owner.OTHER_APP
            else -> Owner.NONE
        }

        /**
         * 지원 목록에서 세션에 넣을 속도·케이던스 종류를 고른다: SPEED, STEPS_PER_MINUTE(없으면 STEPS).
         */
        internal fun motionDataTypesOf(supported: Set<DataType<*, *>>): Set<DataType<*, *>> = buildSet {
            if (DataType.SPEED in supported) add(DataType.SPEED)
            when {
                DataType.STEPS_PER_MINUTE in supported -> add(DataType.STEPS_PER_MINUTE)
                DataType.STEPS in supported -> add(DataType.STEPS)
            }
        }

        /** 신체 활동 권한(ACTIVITY_RECOGNITION). Health Services가 걸음·속도를 주려면 있어야 한다. 없이 넣으면 세션 시작이 거부된다. */
        internal fun activityRecognitionPermitted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED

        /** OS 심박 권한(API 36부터 health.READ_HEART_RATE, 그 전 BODY_SENSORS). 없으면 운동 세션에 심박을 넣으면 시작이 거부된다. */
        private fun heartRatePermitted(context: Context): Boolean {
            val name = if (Build.VERSION.SDK_INT >= API_BAKLAVA) READ_HEART_RATE else Manifest.permission.BODY_SENSORS
            return ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
        }
    }
}
