package com.a207.runkk.watch.run

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.SampleDataPoint
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.watch.testing.FakeSettingsRepository
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.alertPlayer
import com.a207.runkk.watch.testing.runTracker
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * 3.1 WatchMeasurementService(D-368, SQ-44): 포트(Host)로 시작·정지하고, 운동 세션의 위치는 RunTracker.onLocationSample, 1초마다
 * RunTracker.tick, 심박은 LocalWatchGateway로 넘긴다. 자유런 일시정지 때 운동 세션도 멈춘다(D-367, NFR-048).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WatchMeasurementServiceTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `Host는 측정 서비스를 포그라운드로 시작하고 멈춘다`() {
        val host = WatchMeasurementService.Host(context)

        assertEquals(Outcome.Success(Unit), host.startForeground())
        val started: Intent = shadowOf(context).nextStartedService
        assertEquals(WatchMeasurementService::class.java.name, started.component?.className)

        host.stop()
        val stopped: Intent = shadowOf(context).nextStoppedService
        assertEquals(WatchMeasurementService::class.java.name, stopped.component?.className)
    }

    @Test
    fun `측정 중 1초마다 tick하고 심박을 게이트웨이로 넘기며 일시정지를 운동 세션에 맞춘다`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val settings = FakeSettingsRepository()
        val gateway = LocalWatchGateway(alertPlayer(), settings, clock)
        val tracker = runTracker(gateway, settings = settings)
        val client = FakeExerciseClient()
        val source = WatchExerciseSource(client, clock, isHeartRateAllowed = { true }, elapsedRealtimeMillis = { 0 })
        val hearts = mutableListOf<List<HeartRateSample>>()
        backgroundScope.launch { gateway.heartRates.collect { hearts += it } }
        tracker.startRun(RunStartSpec(RunMode.FREE, CardKind.FREE_RUN, RunStartSpec.StartDevice.WATCH))

        val relay = backgroundScope.launch { WatchMeasurementService.relay(tracker, source, gateway, clock) }
        runCurrent()
        advanceTimeBy(2_001)
        assertEquals(Duration.ofSeconds(2), tracker.progress.value.elapsed)

        client.deliver(DataPointContainer(listOf(heart())))
        runCurrent()
        assertEquals(1, hearts.size)

        client.events.clear()
        tracker.onPauseTapped()
        runCurrent()
        tracker.onResumeTapped()
        runCurrent()
        assertEquals(listOf("pause", "resume"), client.events)

        relay.cancel()
        runCurrent()
        assertTrue("end" in client.events)
    }

    @Test
    fun `다른 앱이 운동 중이면 측정을 잇지 않고 실패를 돌려준다`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val settings = FakeSettingsRepository()
        val gateway = LocalWatchGateway(alertPlayer(), settings, clock)
        val tracker = runTracker(gateway, settings = settings)
        val client = FakeExerciseClient().apply { trackedStatus = OTHER_APP_STATUS }
        val source = WatchExerciseSource(client, clock, isHeartRateAllowed = { true }, elapsedRealtimeMillis = { 0 })

        val result = async { WatchMeasurementService.relay(tracker, source, gateway, clock) }
        runCurrent()

        assertEquals(Outcome.Failure(AppError.Rejected(WatchExerciseSource.EXERCISE_IN_USE)), result.await())
    }

    private fun heart() = SampleDataPoint(
        DataType.HEART_RATE_BPM,
        120.0,
        Duration.ofSeconds(1),
        Bundle(),
        HeartRateAccuracy(HeartRateAccuracy.SensorStatus.ACCURACY_HIGH)
    )

    private companion object {
        @SuppressLint("RestrictedApi")
        val OTHER_APP_STATUS = ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS
    }
}
