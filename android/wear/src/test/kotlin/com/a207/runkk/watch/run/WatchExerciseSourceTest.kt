package com.a207.runkk.watch.run

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseGoal
import androidx.health.services.client.data.ExerciseInfo
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseTypeConfig
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.LocationAccuracy
import androidx.health.services.client.data.LocationData
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.data.WarmUpConfig
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.T0
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 3.1 WatchExerciseSource: Health Services 운동 세션 하나로 위치와 심박을 받는다(D-367, NFR-048). 가짜 운동 클라이언트로 본다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WatchExerciseSourceTest {
    private val client = FakeExerciseClient()

    /** 부팅 시각 = T0 − 1시간(가짜 elapsedRealtime 1시간). */
    private val bootInstant: Instant = Instant.ofEpochMilli(T0 - BOOT_MILLIS)

    private fun TestScope.source(isHeartRateAllowed: Boolean = true) = WatchExerciseSource(
        client,
        SchedulerClock(testScheduler),
        isHeartRateAllowed = { isHeartRateAllowed },
        elapsedRealtimeMillis = { BOOT_MILLIS }
    )

    @Test
    fun `달리기 운동 세션을 GPS와 위치·심박으로 연다`() = runTest {
        val source = source()

        assertEquals(Outcome.Success(Unit), source.start())

        val config = checkNotNull(client.config)
        assertEquals(ExerciseType.RUNNING, config.exerciseType)
        assertEquals(setOf(DataType.LOCATION, DataType.HEART_RATE_BPM), config.dataTypes)
        assertTrue(config.isGpsEnabled)
        assertEquals(listOf("callback", "start"), client.events)
    }

    @Test
    fun `심박 동의·권한이 없으면 심박 센서를 켜지 않는다`() = runTest {
        source(isHeartRateAllowed = false).start()

        assertEquals(setOf(DataType.LOCATION), checkNotNull(client.config).dataTypes)
    }

    @Test
    fun `다른 앱이 운동 중이면 열지 않고 EXERCISE_IN_USE로 거부한다`() = runTest {
        client.trackedStatus = OTHER_APP

        val result = source().start()

        assertEquals(Outcome.Failure(AppError.Rejected(WatchExerciseSource.EXERCISE_IN_USE)), result)
        assertNull(client.config)
    }

    @Test
    fun `이 앱이 남긴 세션은 끝내고 새로 연다`() = runTest {
        client.trackedStatus = OWNED

        source().start()

        assertEquals(listOf("end", "callback", "start"), client.events)
    }

    @Test
    fun `위치·심박 표본을 domain 표본으로 바꾸고 믿을 수 없는 심박은 버린다`() = runTest {
        val source = source()
        val locations = mutableListOf<LocationSample>()
        val heartRates = mutableListOf<HeartRateSample>()
        backgroundScope.launch { source.locations().collect { locations += it } }
        backgroundScope.launch { source.heartRates().collect { heartRates += it } }
        source.start()
        runCurrent()

        client.deliver(
            DataPointContainer(
                listOf(
                    location(atBoot = Duration.ofMillis(BOOT_MILLIS + 1_000), accuracy = 4.0),
                    heartRate(atBoot = Duration.ofMillis(BOOT_MILLIS + 1_000), bpm = 141.6, reliable = true),
                    heartRate(atBoot = Duration.ofMillis(BOOT_MILLIS + 2_000), bpm = 150.0, reliable = false)
                )
            )
        )
        runCurrent()

        val at = bootInstant.plusMillis(BOOT_MILLIS + 1_000)
        assertEquals(listOf(LocationSample(at, GeoPoint(37.5, 127.0), 4.0)), locations)
        assertEquals(listOf(HeartRateSample(at, 142)), heartRates)
    }

    @Test
    fun `일시정지·재개·끝은 운동 세션에 그대로 넘기고 끝에서 콜백을 푼다`() = runTest {
        val source = source()
        source.start()
        client.events.clear()

        source.pause()
        source.resume()
        source.end()

        assertEquals(listOf("pause", "resume", "end", "clearCallback"), client.events)
        assertNotNull(client.cleared)
    }

    private fun location(atBoot: Duration, accuracy: Double) = SampleDataPoint(
        DataType.LOCATION,
        LocationData(37.5, 127.0),
        atBoot,
        Bundle(),
        LocationAccuracy(accuracy)
    )

    private fun heartRate(atBoot: Duration, bpm: Double, reliable: Boolean) = SampleDataPoint(
        DataType.HEART_RATE_BPM,
        bpm,
        atBoot,
        Bundle(),
        HeartRateAccuracy(
            if (reliable) HeartRateAccuracy.SensorStatus.ACCURACY_HIGH else HeartRateAccuracy.SensorStatus.NO_CONTACT
        )
    )

    private companion object {
        const val BOOT_MILLIS = 3_600_000L

        @SuppressLint("RestrictedApi")
        val OWNED = ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS

        @SuppressLint("RestrictedApi")
        val OTHER_APP = ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS
    }
}

/** 가짜 ExerciseClient. 부른 순서를 [events]에 남기고, 등록된 콜백으로 [deliver]가 측정값을 흘린다. */
internal class FakeExerciseClient : ExerciseClient {
    @SuppressLint("RestrictedApi")
    var trackedStatus: Int = ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS
    var config: ExerciseConfig? = null
    var cleared: ExerciseUpdateCallback? = null
    val events = mutableListOf<String>()
    private var callback: ExerciseUpdateCallback? = null
    private var onMetrics: ((DataPointContainer) -> Unit)? = null

    fun deliver(container: DataPointContainer) {
        checkNotNull(onMetrics) { "콜백이 없다" }(container)
    }

    @Suppress("UNCHECKED_CAST")
    private fun done(): ListenableFuture<Void> = Futures.immediateVoidFuture() as ListenableFuture<Void>

    override fun getCurrentExerciseInfoAsync(): ListenableFuture<ExerciseInfo> =
        Futures.immediateFuture(ExerciseInfo(trackedStatus, ExerciseType.RUNNING))

    override fun startExerciseAsync(configuration: ExerciseConfig): ListenableFuture<Void> {
        events += "start"
        config = configuration
        return done()
    }

    override fun pauseExerciseAsync(): ListenableFuture<Void> = done().also { events += "pause" }

    override fun resumeExerciseAsync(): ListenableFuture<Void> = done().also { events += "resume" }

    override fun endExerciseAsync(): ListenableFuture<Void> = done().also { events += "end" }

    override fun setUpdateCallback(callback: ExerciseUpdateCallback) {
        events += "callback"
        this.callback = callback
        onMetrics = (callback as? WatchExerciseSource.MetricsReceiver)?.let { it::onMetrics }
    }

    override fun setUpdateCallback(executor: Executor, callback: ExerciseUpdateCallback) = setUpdateCallback(callback)

    override fun clearUpdateCallbackAsync(callback: ExerciseUpdateCallback): ListenableFuture<Void> {
        events += "clearCallback"
        cleared = callback
        return done()
    }

    override fun prepareExerciseAsync(configuration: WarmUpConfig): ListenableFuture<Void> = done()

    override fun flushAsync(): ListenableFuture<Void> = done()

    override fun markLapAsync(): ListenableFuture<Void> = done()

    override fun addGoalToActiveExerciseAsync(exerciseGoal: ExerciseGoal<*>): ListenableFuture<Void> = done()

    override fun removeGoalFromActiveExerciseAsync(exerciseGoal: ExerciseGoal<*>): ListenableFuture<Void> = done()

    override fun overrideAutoPauseAndResumeForActiveExerciseAsync(enabled: Boolean): ListenableFuture<Void> = done()

    override fun overrideBatchingModesForActiveExerciseAsync(batchingModes: Set<BatchingMode>): ListenableFuture<Void> =
        done()

    override fun getCapabilitiesAsync(): ListenableFuture<ExerciseCapabilities> = error("쓰지 않는다")

    override fun updateExerciseTypeConfigAsync(exerciseTypeConfig: ExerciseTypeConfig): ListenableFuture<Void> = done()
}
