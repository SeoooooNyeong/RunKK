package com.a207.runkk.watch.run

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.LocationAccuracy
import androidx.health.services.client.data.LocationData
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
        // 정지도 서비스 명령으로 보낸다: 시작 명령보다 먼저 서비스를 멈춰 포그라운드 5초 제한에 걸리지 않게(남은 위험 M5, WR12-10).
        val stopped: Intent = shadowOf(context).nextStartedService
        assertEquals(WatchMeasurementService::class.java.name, stopped.component?.className)
        assertEquals(WatchMeasurementService.ACTION_STOP, stopped.action)
    }

    @Test
    fun `막 만들어진 서비스가 정지 명령을 받으면 포그라운드를 내리고 그 명령 번호로 멈춘다(WR13-4, M5)`() {
        val controller = Robolectric.buildService(WatchMeasurementService::class.java, stopCommand()).create()

        controller.startCommand(0, 1)

        val service = shadowOf(controller.get())
        assertTrue(service.isForegroundStopped)
        assertTrue(service.notificationShouldRemoved)
        assertTrue(service.isStoppedBySelf)
        assertEquals(1, service.stopSelfId)
    }

    @Test
    fun `시작 명령으로 포그라운드에 오른 뒤 정지 명령을 받으면 내리고 정지 명령 번호로만 멈춘다(WR13-4, stopSelf(startId))`() {
        shadowOf(
            context
        ).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        val controller = Robolectric.buildService(WatchMeasurementService::class.java).create()
        val service = controller.get()

        service.onStartCommand(Intent(context, WatchMeasurementService::class.java), 0, 1)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertFalse(shadowOf(service).isForegroundStopped)

        // 정지 명령(2번) 뒤에 새 시작 명령(3번)이 와 있어도 OS는 2번까지만 멈춘다: stopSelf()가 아니라 stopSelf(2)여야 한다.
        service.onStartCommand(stopCommand(), 0, 2)

        assertTrue(shadowOf(service).isForegroundStopped)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(2, shadowOf(service).stopSelfId)
        controller.destroy()
    }

    private fun stopCommand(): Intent =
        Intent(context, WatchMeasurementService::class.java).setAction(WatchMeasurementService.ACTION_STOP)

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

    @Test
    fun `이전 운동 세션의 끝 알림이 시작 직후 와도 측정을 잇고 이번 세션이 끝나면 EXERCISE_ENDED로 끝낸다`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val settings = FakeSettingsRepository()
        val gateway = LocalWatchGateway(alertPlayer(), settings, clock)
        val tracker = runTracker(gateway, settings = settings)
        val client = FakeExerciseClient(startTime = clock::instant).apply { trackedStatus = OWNED_STATUS }
        val source = WatchExerciseSource(client, clock, isHeartRateAllowed = { true }, elapsedRealtimeMillis = { 0 })
        tracker.startRun(RunStartSpec(RunMode.FREE, CardKind.FREE_RUN, RunStartSpec.StartDevice.WATCH))
        val previousStart = clock.instant().minus(Duration.ofMinutes(5))

        val result = async { WatchMeasurementService.relay(tracker, source, gateway, clock) }
        runCurrent()
        client.deliverEnded(startTime = previousStart)
        advanceTimeBy(3_001)
        assertFalse(result.isCompleted)
        assertEquals(Duration.ofSeconds(3), tracker.progress.value.elapsed)

        client.deliverEnded()
        runCurrent()
        assertEquals(Outcome.Failure(AppError.Rejected(WatchMeasurementService.EXERCISE_ENDED)), result.await())
    }

    @Test
    fun `워치 자유런은 GPS 점프를 버리고 묶여 온 정상 좌표로 거리를 계속 기록한다`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val settings = FakeSettingsRepository()
        val gateway = LocalWatchGateway(alertPlayer(), settings, clock)
        val tracker = runTracker(gateway, settings = settings)
        val client = FakeExerciseClient()
        val source = WatchExerciseSource(client, clock, isHeartRateAllowed = { false }, elapsedRealtimeMillis = { 0 })
        tracker.startRun(RunStartSpec(RunMode.FREE, CardKind.FREE_RUN, RunStartSpec.StartDevice.WATCH))
        backgroundScope.launch { WatchMeasurementService.relay(tracker, source, gateway, clock) }
        runCurrent()

        client.deliver(DataPointContainer(listOf(location(1, 127.0), location(2, 127.001))))
        runCurrent()
        assertEquals(1, tracker.routeLine.value.points.size)

        client.deliver(
            DataPointContainer(listOf(location(5, 127.000075), location(4, 127.00005), location(3, 127.000025)))
        )
        runCurrent()
        advanceTimeBy(5_001)
        assertEquals(4, tracker.routeLine.value.points.size)
        assertTrue(tracker.progress.value.distance.meters in 1..6)
    }

    private fun location(second: Long, longitude: Double) = SampleDataPoint(
        DataType.LOCATION,
        LocationData(37.5, longitude),
        Duration.ofSeconds(second),
        Bundle(),
        LocationAccuracy(5.0)
    )

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

        @SuppressLint("RestrictedApi")
        val OWNED_STATUS = ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS
    }

    @Test
    fun `저전력 표시면 진행 중 활동을 1분에 한 번만 고치고 화면이 켜져 있으면 늘 고친다(D-382, FR-256)`() {
        val minute = WatchMeasurementService.AMBIENT_UPDATE_MILLIS
        assertTrue(WatchMeasurementService.shouldUpdate(isInteractive = true, shownAtMillis = 1_000, nowMillis = 1_001))
        assertTrue(WatchMeasurementService.shouldUpdate(isInteractive = false, shownAtMillis = null, nowMillis = 5))
        kotlin.test.assertFalse(
            WatchMeasurementService.shouldUpdate(isInteractive = false, shownAtMillis = 0, nowMillis = minute - 1)
        )
        assertTrue(WatchMeasurementService.shouldUpdate(isInteractive = false, shownAtMillis = 0, nowMillis = minute))
    }
}
