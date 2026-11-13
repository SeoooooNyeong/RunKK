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
import androidx.health.services.client.data.ExerciseTypeCapabilities
import androidx.health.services.client.data.ExerciseTypeConfig
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.IntervalDataPoint
import androidx.health.services.client.data.LocationAccuracy
import androidx.health.services.client.data.LocationAvailability
import androidx.health.services.client.data.LocationData
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.data.WarmUpConfig
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.MotionSample
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.T0
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private fun TestScope.source(isHeartRateAllowed: Boolean = true, isMotionAllowed: Boolean = false) =
        WatchExerciseSource(
            client,
            SchedulerClock(testScheduler),
            isHeartRateAllowed = { isHeartRateAllowed },
            elapsedRealtimeMillis = { BOOT_MILLIS },
            isMotionAllowed = { isMotionAllowed }
        )

    private fun supports(vararg types: DataType<*, *>) {
        client.capabilities = ExerciseCapabilities(
            mapOf(ExerciseType.RUNNING to ExerciseTypeCapabilities(types.toSet(), emptyMap(), emptyMap(), false))
        )
    }

    @Test
    fun `화면에서 GPS를 준비하고 시작 입력까지 세션을 유지한다`() = runTest {
        val source = source()
        val ready = CompletableDeferred<Boolean>()
        val requested = CompletableDeferred<Unit>()
        val starting = async {
            source.start(gpsWarmUpMillis = 60_000) {
                ready.complete(it)
                requested.await()
            }
        }
        runCurrent()

        val callback = checkNotNull(client.currentCallback)
        callback.onAvailabilityChanged(DataType.LOCATION, LocationAvailability.ACQUIRED_UNTETHERED)
        assertTrue(ready.await())
        assertEquals(listOf("callback", "prepare"), client.events)

        requested.complete(Unit)
        assertEquals(Outcome.Success(Unit), starting.await())
        assertEquals(listOf("callback", "prepare", "start"), client.events)
    }

    @Test
    fun `위치가 준비되면 같은 콜백으로 예열을 끝내지 않고 운동을 시작한다`() = runTest {
        val source = source()
        val starting = async { source.start(gpsWarmUpMillis = 60_000) }
        runCurrent()

        assertEquals(listOf("callback", "prepare"), client.events)
        val callback = checkNotNull(client.currentCallback)
        callback.onAvailabilityChanged(DataType.LOCATION, LocationAvailability.ACQUIRED_UNTETHERED)
        assertEquals(Outcome.Success(Unit), starting.await())
        assertEquals(listOf("callback", "prepare", "start"), client.events)
    }

    @Test
    fun `예열이 시간 초과되어도 준비 세션을 끊지 않고 시작한다`() = runTest {
        assertEquals(Outcome.Success(Unit), source().start(gpsWarmUpMillis = 1))
        assertEquals(listOf("callback", "prepare", "start"), client.events)
    }

    @Test
    fun `예열 중 취소하면 운동을 시작하지 않고 준비 세션과 콜백을 정리한다`() = runTest {
        val source = source()
        val starting = launch { source.start(gpsWarmUpMillis = 60_000) }
        runCurrent()
        starting.cancelAndJoin()

        assertEquals(listOf("callback", "prepare", "end", "clearCallback"), client.events)
    }

    @Test
    fun `신체 활동 권한이 있고 기기가 지원하면 속도·분당 걸음 수를 세션에 넣는다(S15P31A207-242)`() = runTest {
        supports(DataType.LOCATION, DataType.HEART_RATE_BPM, DataType.SPEED, DataType.STEPS_PER_MINUTE, DataType.STEPS)

        source(isMotionAllowed = true).start()

        assertEquals(
            setOf(DataType.LOCATION, DataType.HEART_RATE_BPM, DataType.SPEED, DataType.STEPS_PER_MINUTE),
            checkNotNull(client.config).dataTypes
        )
    }

    @Test
    fun `분당 걸음 수가 없으면 걸음 수를, 권한이 없거나 능력을 읽지 못하면 넣지 않는다`() = runTest {
        supports(DataType.LOCATION, DataType.STEPS)
        source(isMotionAllowed = true).start()
        assertEquals(
            setOf(DataType.LOCATION, DataType.HEART_RATE_BPM, DataType.STEPS),
            checkNotNull(client.config).dataTypes
        )

        source(isMotionAllowed = false).start()
        assertEquals(setOf(DataType.LOCATION, DataType.HEART_RATE_BPM), checkNotNull(client.config).dataTypes)

        client.capabilities = null
        assertEquals(Outcome.Success(Unit), source(isMotionAllowed = true).start(), "능력 조회 실패에도 세션은 연다")
        assertEquals(setOf(DataType.LOCATION, DataType.HEART_RATE_BPM), checkNotNull(client.config).dataTypes)
    }

    @Test
    fun `속도·분당 걸음 수·걸음 수를 움직임 표본으로 바꾸고 쓸 수 없는 값은 버린다`() = runTest {
        supports(DataType.SPEED, DataType.STEPS_PER_MINUTE)
        val source = source(isMotionAllowed = true)
        val motions = mutableListOf<MotionSample>()
        backgroundScope.launch { source.motions().collect { motions += it } }
        source.start()
        runCurrent()

        client.deliver(
            DataPointContainer(
                listOf(
                    SampleDataPoint(DataType.SPEED, 3.2, Duration.ofMillis(BOOT_MILLIS + 1_000), Bundle()),
                    SampleDataPoint(DataType.SPEED, Double.NaN, Duration.ofMillis(BOOT_MILLIS + 1_500), Bundle()),
                    SampleDataPoint(DataType.STEPS_PER_MINUTE, 172L, Duration.ofMillis(BOOT_MILLIS + 2_000), Bundle())
                )
            )
        )
        // STEPS만 주는 기기: 0~6초 동안 18걸음 = 분당 180걸음
        client.deliver(
            DataPointContainer(
                listOf(
                    IntervalDataPoint(
                        DataType.STEPS,
                        0L,
                        Duration.ofMillis(BOOT_MILLIS),
                        Duration.ofMillis(BOOT_MILLIS),
                        Bundle()
                    ),
                    IntervalDataPoint(
                        DataType.STEPS,
                        18L,
                        Duration.ofMillis(BOOT_MILLIS),
                        Duration.ofMillis(BOOT_MILLIS + 6_000),
                        Bundle()
                    )
                )
            )
        )
        runCurrent()

        val origin = bootInstant.plusMillis(BOOT_MILLIS)
        assertEquals(
            listOf(
                MotionSample(origin.plusMillis(1_000), speedMps = 3.2),
                MotionSample(origin.plusMillis(2_000), cadenceSpm = 172.0),
                MotionSample(origin.plusMillis(6_000), cadenceSpm = 180.0)
            ),
            motions
        )
    }

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
        backgroundScope.launch { source.heartRates().collect { heartRates += it } }
        source.start(onLocations = { locations += it })
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

    @Test
    fun `끝내는 중에 새로 시작하면 끝내기가 콜백을 푼 뒤에 연다(남은 위험 M2, WR12-9)`() = runTest {
        val source = source()
        source.start()
        client.events.clear()
        val endGate = SettableFuture.create<Void?>()
        client.endFuture = endGate

        val ending = launch { source.end() }
        runCurrent()
        val starting = launch { source.start() }
        runCurrent()
        endGate.set(null)
        ending.join()
        starting.join()

        assertEquals(listOf("end", "clearCallback", "callback", "start"), client.events)
    }

    @Test
    fun `끝낸 뒤 늦게 온 운동 끝 알림은 측정 끝으로 보내지 않는다(WR12-9)`() = runTest {
        val source = source()
        val ended = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.ended.collect { ended += it } }
        source.start()
        client.deliverEnded()
        assertEquals(1, ended.size)

        source.end()
        client.deliverEnded()

        assertEquals(1, ended.size)
    }

    @Test
    fun `새 세션을 연 뒤에 온 이전 세션의 끝 알림은 측정 끝으로 보내지 않고 이번 세션의 끝만 보낸다`() = runTest {
        val source = source()
        val ended = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.ended.collect { ended += it } }
        // 이 앱이 남긴 세션(동행 심박 수집, 지난 측정)을 끝내고 새로 연다. 그 세션의 끝 알림은 새 콜백에 늦게 올 수 있다.
        client.trackedStatus = OWNED

        source.start()
        client.deliverEnded(startTime = Instant.ofEpochMilli(T0).minus(Duration.ofMinutes(10)))
        assertEquals(0, ended.size)

        client.deliverEnded()
        assertEquals(1, ended.size)
    }

    @Test
    fun `화면 꺼짐 중 모인 위치 300개를 측정 순서대로 한 번에 넘긴다`() = runTest {
        val batches = mutableListOf<List<LocationSample>>()
        val source = source()
        source.start(onLocations = { batches += it })
        val points = (1L..300L).reversed().map { second ->
            location(Duration.ofMillis(BOOT_MILLIS + second * 1_000), accuracy = 4.0)
        }

        client.deliver(DataPointContainer(points))

        assertEquals(
            (1L..300L).map {
                bootInstant.plusMillis(BOOT_MILLIS + it * 1_000)
            },
            batches.single().map { it.at }
        )
    }

    @Test
    fun `저장용 운동 종료는 마지막 위치 묶음과 종료 알림을 기다린다`() = runTest {
        val batches = mutableListOf<List<LocationSample>>()
        val source = source()
        source.start(onLocations = { batches += it })
        val finishing = async { source.finish(bootInstant.plusMillis(BOOT_MILLIS + 1_000)) }
        runCurrent()
        assertTrue(!finishing.isCompleted)

        val point = location(Duration.ofMillis(BOOT_MILLIS + 1_000), accuracy = 4.0)
        client.deliver(DataPointContainer(listOf(point)))
        client.deliverEnded()
        finishing.await()

        assertEquals(1, batches.single().size)
        assertEquals(listOf("callback", "start", "end", "clearCallback"), client.events)
    }

    @Test
    fun `준비 화면과 실제 운동이 같은 GPS 콜백을 이어 쓴다`() = runTest {
        val source = source()
        val preparing = backgroundScope.launch { source.gpsReadiness().collect {} }
        runCurrent()

        val callback = checkNotNull(client.currentCallback)
        callback.onAvailabilityChanged(DataType.LOCATION, LocationAvailability.ACQUIRED_UNTETHERED)
        assertEquals(Outcome.Success(Unit), source.start())
        preparing.cancelAndJoin()

        assertEquals(callback, client.currentCallback)
        assertEquals(listOf("callback", "prepare", "start"), client.events)
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
internal class FakeExerciseClient(
    /** 운동 세션이 시작되는 시각. Health Services처럼 시작 요청을 받은 뒤의 시각이다. */
    private val startTime: () -> Instant = { Instant.ofEpochMilli(T0) }
) : ExerciseClient {
    @SuppressLint("RestrictedApi")
    var trackedStatus: Int = ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS
    var config: ExerciseConfig? = null

    /** 기기 운동 능력. null이면 조회가 실패한다. */
    var capabilities: ExerciseCapabilities? = null
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
        exerciseStartTime = startTime()
        return done()
    }

    override fun pauseExerciseAsync(): ListenableFuture<Void> = done().also { events += "pause" }

    override fun resumeExerciseAsync(): ListenableFuture<Void> = done().also { events += "resume" }

    /** 있으면 endExerciseAsync가 이 미래 값을 돌려준다(끝내기가 늦게 끝나는 경우). */
    var endFuture: ListenableFuture<Void?>? = null

    @Suppress("UNCHECKED_CAST")
    override fun endExerciseAsync(): ListenableFuture<Void> =
        ((endFuture as ListenableFuture<Void>?) ?: done()).also { events += "end" }

    /** 마지막으로 시작한 운동 세션의 시작 시각([startTime]이 찍는다). */
    var exerciseStartTime: Instant? = null

    /**
     * 등록된 콜백에 운동 세션이 끝났다고 알린다. [startTime]은 끝난 세션의 시작 시각이고, 기본은 마지막으로 시작한 세션이다. 이전 세션의
     * 늦은 끝 알림은 그 세션의 시작 시각을 넘긴다.
     */
    fun deliverEnded(startTime: Instant? = exerciseStartTime) {
        checkNotNull(callback as? WatchExerciseSource.MetricsReceiver) { "콜백이 없다" }
            .onState(isEnded = true, startTime = startTime, endReason = USER_END)
    }

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

    /** 마지막 예열 요청(GPS 예열, W53). */
    var warmUp: WarmUpConfig? = null

    /** 지금 걸린 콜백(예열 시험이 가용성·상태를 보낸다). */
    val currentCallback: ExerciseUpdateCallback? get() = callback

    override fun prepareExerciseAsync(configuration: WarmUpConfig): ListenableFuture<Void> {
        events += "prepare"
        warmUp = configuration
        return done()
    }

    override fun flushAsync(): ListenableFuture<Void> = done()

    override fun markLapAsync(): ListenableFuture<Void> = done()

    override fun addGoalToActiveExerciseAsync(exerciseGoal: ExerciseGoal<*>): ListenableFuture<Void> = done()

    override fun removeGoalFromActiveExerciseAsync(exerciseGoal: ExerciseGoal<*>): ListenableFuture<Void> = done()

    override fun overrideAutoPauseAndResumeForActiveExerciseAsync(enabled: Boolean): ListenableFuture<Void> = done()

    override fun overrideBatchingModesForActiveExerciseAsync(batchingModes: Set<BatchingMode>): ListenableFuture<Void> =
        done()

    override fun getCapabilitiesAsync(): ListenableFuture<ExerciseCapabilities> =
        capabilities?.let { Futures.immediateFuture(it) }
            ?: Futures.immediateFailedFuture(IllegalStateException("능력 없음"))

    override fun updateExerciseTypeConfigAsync(exerciseTypeConfig: ExerciseTypeConfig): ListenableFuture<Void> = done()

    private companion object {
        /** ExerciseEndReason.USER_END */
        const val USER_END = 4
    }
}
