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
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LocationSample
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
 * 동행 모드 심박 수집(watch.heart)과 같은 운동 세션을 다투지 않는다: 단독 측정 중에는 폰 측정이 막혀 있다(D-373 (3)).
 */
@Singleton
class WatchExerciseSource internal constructor(
    private val exerciseClient: ExerciseClient,
    private val clock: Clock,
    private val isHeartRateAllowed: () -> Boolean,
    private val elapsedRealtimeMillis: () -> Long
) {
    @Inject
    constructor(@ApplicationContext context: Context, clock: Clock, settings: SettingsRepository) : this(
        HealthServices.getClient(context).exerciseClient,
        clock,
        isHeartRateAllowed = {
            settings.heartRateConsented() && settings.watchLinked() && heartRatePermitted(context)
        },
        elapsedRealtimeMillis = SystemClock::elapsedRealtime
    )

    private val locationSamples = MutableSharedFlow<LocationSample>(extraBufferCapacity = SAMPLE_BUFFER)
    private val heartRateSamples = MutableSharedFlow<HeartRateSample>(extraBufferCapacity = SAMPLE_BUFFER)
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
        exerciseClient.setUpdateCallback(receiver)
        val dataTypes = if (isHeartRateAllowed()) {
            setOf(DataType.LOCATION, DataType.HEART_RATE_BPM)
        } else {
            setOf(DataType.LOCATION)
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

    /** 위치 표본(측정일시, 좌표, 수평 정확도 반경 m). 정확도를 모르면 Double.MAX_VALUE(domain LocationFilter가 거른다). */
    fun locations(): Flow<LocationSample> = locationSamples.asSharedFlow()

    /** 믿을 수 있는 심박 표본. */
    fun heartRates(): Flow<HeartRateSample> = heartRateSamples.asSharedFlow()

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
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            Timber.w(throwable, "운동 세션 콜백 등록 실패")
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit
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

        /** OS 심박 권한(API 36부터 health.READ_HEART_RATE, 그 전 BODY_SENSORS). 없으면 운동 세션에 심박을 넣으면 시작이 거부된다. */
        private fun heartRatePermitted(context: Context): Boolean {
            val name = if (Build.VERSION.SDK_INT >= API_BAKLAVA) READ_HEART_RATE else Manifest.permission.BODY_SENSORS
            return ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
        }
    }
}
