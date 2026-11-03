package com.a207.runkk.watch.run

import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.watch.alert.ProximityAlertPlayer
import com.a207.runkk.watch.testing.FakeAlertOutput
import com.a207.runkk.watch.testing.FakeSettingsRepository
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.SchedulerMonotonicClock
import com.a207.runkk.watch.testing.alertPlayer
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 3.1 LocalWatchGateway: 워치 단독 측정에서 WatchGateway를 워치 안에서 끝낸다(D-369, H-58, FR-196, FR-188, FR-041). */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalWatchGatewayTest {
    private val settings = FakeSettingsRepository()

    private class Harness(val gateway: LocalWatchGateway, val player: ProximityAlertPlayer, val output: FakeAlertOutput)

    private fun TestScope.harness(): Harness {
        val output = FakeAlertOutput(SchedulerMonotonicClock(testScheduler))
        val player = alertPlayer(output)
        return Harness(LocalWatchGateway(player, settings, SchedulerClock(testScheduler)), player, output)
    }

    @Test
    fun `H-58 근접 알림을 ProximityMessage로 바꿔 워치 재생기에 바로 넘긴다`() = runTest {
        val h = harness()

        h.gateway.publishProximity(ProximityAlert(gapMeters = 20, interval = Duration.ofMillis(500), isAhead = true))
        runCurrent()
        advanceTimeBy(1_100)

        assertTrue(h.player.isPlaying.value)
        assertEquals(listOf(0L, 500L, 1_000L), h.output.beepTimes())
    }

    @Test
    fun `근접 알림 끔(null)이면 재생을 바로 멈춘다`() = runTest {
        val h = harness()
        h.gateway.publishProximity(ProximityAlert(20, Duration.ofMillis(500), isAhead = false))
        runCurrent()

        h.gateway.publishProximity(null)

        assertFalse(h.player.isPlaying.value)
    }

    @Test
    fun `추월·역전은 1초 긴 진동 한 번이다`() = runTest {
        val h = harness()

        h.gateway.publishLeadChange(Guidance.Kind.OVERTAKEN)
        runCurrent()

        assertEquals(listOf(0L), h.output.longVibrationTimes(minMillis = 1_000))
    }

    @Test
    fun `심박은 심박 동의와 워치연동이 모두 켜져 있을 때만 내보낸다`() = runTest {
        val h = harness()
        val received = mutableListOf<List<HeartRateSample>>()
        backgroundScope.launch { h.gateway.heartRates.collect { received += it } }
        runCurrent()
        val sample = HeartRateSample(Instant.ofEpochSecond(1), 140)

        h.gateway.offerHeartRates(listOf(sample))
        settings.isHeartRateConsented = false
        h.gateway.offerHeartRates(listOf(sample.copy(bpm = 141)))
        settings.isHeartRateConsented = true
        settings.isWatchLinked = false
        h.gateway.offerHeartRates(listOf(sample.copy(bpm = 142)))
        runCurrent()

        assertEquals(listOf(listOf(sample)), received)
    }

    @Test
    fun `워치 안에서 끝나므로 늘 연결되어 있고 자기 자신을 측정 중으로 보지 않는다`() = runTest {
        val h = harness()

        assertTrue(h.gateway.isConnected())
        assertFalse(h.gateway.isWatchMeasuring())
    }
}
