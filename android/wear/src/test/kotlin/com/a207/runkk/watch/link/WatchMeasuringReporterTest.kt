package com.a207.runkk.watch.link

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.watch.heart.HeartRateCollector
import com.a207.runkk.watch.run.LocalWatchGateway
import com.a207.runkk.watch.testing.FakeHeartRateSensor
import com.a207.runkk.watch.testing.FakeSensorPermission
import com.a207.runkk.watch.testing.FakeSettingsRepository
import com.a207.runkk.watch.testing.FakeWearDataLayer
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.T0
import com.a207.runkk.watch.testing.alertPlayer
import com.a207.runkk.watch.testing.runTracker
import com.a207.runkk.wear.contract.WatchMeasuringMessage
import com.a207.runkk.wear.contract.WearPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 2.2 WatchMeasuringReporter(D-373, D-390, D-391 M77): RunTracker의 측정 여부를 구독해 바뀔 때와 측정 중 30초마다 WATCH_MEASURING을
 * 보낸다. 워치 쪽 H-57: 앱이 죽어 거짓을 못 보내도 폰이 90초 뒤 풀 수 있게, 측정 중에는 참을 계속 다시 보낸다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchMeasuringReporterTest {
    private val dataLayer = FakeWearDataLayer()

    @Test
    fun `H-57 측정 시작에 참, 측정 중 30초마다 참, 끝에 거짓을 보낸다`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val settings = FakeSettingsRepository()
        val tracker = runTracker(LocalWatchGateway(alertPlayer(), settings, clock), settings = settings)
        val collector = HeartRateCollector(FakeHeartRateSensor(), FakeSensorPermission(), backgroundScope)
        val link = PhoneLink(dataLayer, collector, alertPlayer(), clock, backgroundScope)
        WatchMeasuringReporter(tracker, link, backgroundScope).start()
        runCurrent()
        assertEquals(emptyList(), measuring())

        tracker.startRun(RunStartSpec(RunMode.FREE, CardKind.FREE_RUN, RunStartSpec.StartDevice.WATCH))
        runCurrent()
        advanceTimeBy(65_000)
        tracker.onFinishLongPressed()
        runCurrent()
        advanceTimeBy(60_000)

        assertEquals(
            listOf(true to 0L, true to 30_000L, true to 60_000L, false to 65_000L),
            measuring().map { it.isMeasuring to it.atEpochMillis - T0 }
        )
    }

    private fun measuring(): List<WatchMeasuringMessage> = dataLayer.sentMessages
        .filter { it.second == WearPaths.WATCH_MEASURING }
        .map { WatchMeasuringMessage.decode(it.third) }
}
