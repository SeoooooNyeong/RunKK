package com.a207.runkk.watch.ui

import com.a207.runkk.watch.heart.HeartRateCollector
import com.a207.runkk.watch.link.PhoneLink
import com.a207.runkk.watch.link.WearMessage
import com.a207.runkk.watch.testing.FakeHeartRateSensor
import com.a207.runkk.watch.testing.FakeSensorPermission
import com.a207.runkk.watch.testing.FakeWearDataLayer
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WearPaths
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

/**
 * 수집 중 포그라운드 서비스 수명(NFR-023): 수집이 켜지면 서비스를 띄우고, 금지 플래그·러닝 끝·30분 침묵으로
 * 수집이 멈추면 서비스가 내려간다. 백그라운드라 못 띄웠으면 화면이 앞으로 올 때 다시 띄운다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionForegroundKeeperTest {
    private val dataLayer = FakeWearDataLayer()
    private val sensor = FakeHeartRateSensor()
    private val launcher = FakeLauncher()

    private class FakeLauncher : ForegroundServiceLauncher {
        var launches = 0
        var allowed = true

        override fun launch(): Boolean {
            launches++
            return allowed
        }
    }

    private fun TestScope.keeper(): CollectionForegroundKeeper {
        val collector = HeartRateCollector(sensor, FakeSensorPermission(isGranted = true), backgroundScope)
        val link = PhoneLink(dataLayer, collector, SchedulerClock(testScheduler), backgroundScope)
        link.start()
        return CollectionForegroundKeeper(link, launcher, backgroundScope).also {
            it.start()
            runCurrent()
        }
    }

    private fun TestScope.receive(running: Boolean = true, allowed: Boolean = true) {
        val message = WatchDisplayMessage(
            running = running,
            elapsedSeconds = 60,
            distanceMeters = 200,
            currentPaceSecondsPerKm = 300,
            heartRateBpm = null,
            planDistanceMeters = null,
            planPaceSecondsPerKm = null,
            heartRateCollectionAllowed = allowed,
            notice = null
        )
        dataLayer.incoming.tryEmit(WearMessage(WearPaths.WATCH_DISPLAY, message.encode()))
        runCurrent()
    }

    /** 서비스가 하는 일: 수집이 멈추길 기다렸다 내려간다. 끝났으면 참을 남긴다. */
    private fun TestScope.serviceWatch(keeper: CollectionForegroundKeeper): () -> Boolean {
        var ended = false
        backgroundScope.launch {
            keeper.awaitCollectionEnd()
            keeper.onServiceStopped()
            ended = true
        }
        runCurrent()
        return { ended }
    }

    @Test
    fun `수집이 켜지기 전에는 서비스를 띄우지 않는다`() = runTest {
        val keeper = keeper()

        receive(running = false)
        receive(running = true, allowed = false)
        keeper.onAppForeground()

        assertEquals(0, launcher.launches)
    }

    @Test
    fun `러닝 중 수집이 켜지면 서비스를 한 번만 띄운다`() = runTest {
        keeper()

        repeat(5) { receive() }

        assertEquals(1, launcher.launches)
    }

    @Test
    fun `수집 금지 플래그가 오면 서비스가 내려간다`() = runTest {
        val keeper = keeper()
        receive()
        val ended = serviceWatch(keeper)
        assertFalse(ended())

        receive(running = true, allowed = false)

        assertTrue(ended())
    }

    @Test
    fun `러닝이 끝나면 서비스가 내려가고 다음 러닝에 다시 띄운다`() = runTest {
        val keeper = keeper()
        receive()
        val ended = serviceWatch(keeper)

        receive(running = false)
        assertTrue(ended())
        receive()

        assertEquals(2, launcher.launches)
    }

    @Test
    fun `폰 메시지가 30분 없어 수집이 멈추면 서비스도 내려간다`() = runTest {
        val keeper = keeper()
        receive()
        val ended = serviceWatch(keeper)

        advanceTimeBy(PhoneLink.MAX_SILENT_RUN_MILLIS - 1)
        runCurrent()
        assertFalse(ended())
        advanceTimeBy(2)
        runCurrent()

        assertTrue(ended())
        assertEquals(0, sensor.subscribers)
    }

    @Test
    fun `백그라운드라 못 띄웠으면 화면이 앞으로 올 때 다시 띄운다`() = runTest {
        val keeper = keeper()
        launcher.allowed = false
        receive()
        assertEquals(1, launcher.launches)

        launcher.allowed = true
        keeper.onAppForeground()
        keeper.onAppForeground()

        assertEquals(2, launcher.launches)
    }

    @Test
    fun `서비스가 시스템에 의해 내려가면 화면이 앞으로 올 때 다시 띄운다`() = runTest {
        val keeper = keeper()
        receive()

        keeper.onServiceStopped()
        keeper.onAppForeground()

        assertEquals(2, launcher.launches)
    }
}
