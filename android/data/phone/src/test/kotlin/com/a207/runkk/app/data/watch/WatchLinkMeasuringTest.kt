package com.a207.runkk.app.data.watch

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.wear.contract.WatchMeasuringMessage
import com.a207.runkk.wear.contract.WearPaths
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * WatchLink.isWatchMeasuring(3.6, D-373 (2), D-390, SQ-47, H-57): WATCH_MEASURING의 마지막 값으로 답하고, 마지막 참이 90초보다
 * 오래되거나 연결이 끊기면 거짓이다("모르면 막지 않는다").
 */
class WatchLinkMeasuringTest {
    private val transport = RecordingTransport()
    private val clock = MutableClock(T0)

    @Test
    fun `아무것도 받지 않았으면 거짓이다`() = runTest {
        assertFalse(link().isWatchMeasuring())
    }

    @Test
    fun `측정 중 참을 받으면 참이고 거짓을 받으면 거짓이다`() = runTest {
        val link = link()

        transport.receive(WearPaths.WATCH_MEASURING, measuring(true, T0))
        assertTrue(link.isWatchMeasuring())

        transport.receive(WearPaths.WATCH_MEASURING, measuring(false, T0.plusSeconds(5)))
        assertFalse(link.isWatchMeasuring())
    }

    @Test
    fun `마지막 참 뒤 90초까지는 참이고 그보다 오래되면 거짓이다(H-57 워치 앱 강제 종료)`() = runTest {
        val link = link()
        transport.receive(WearPaths.WATCH_MEASURING, measuring(true, T0))

        clock.now = T0.plusSeconds(90)
        assertTrue(link.isWatchMeasuring())

        clock.now = T0.plusSeconds(90).plusMillis(1)
        assertFalse(link.isWatchMeasuring())
    }

    @Test
    fun `30초마다 다시 오는 참이 만료 시각을 늦춘다`() = runTest {
        val link = link()
        transport.receive(WearPaths.WATCH_MEASURING, measuring(true, T0))
        clock.now = T0.plusSeconds(60)
        transport.receive(WearPaths.WATCH_MEASURING, measuring(true, T0.plusSeconds(60)))

        clock.now = T0.plusSeconds(140)

        assertTrue(link.isWatchMeasuring())
    }

    @Test
    fun `연결이 끊기면 거짓이다`() = runTest {
        val link = link()
        transport.receive(WearPaths.WATCH_MEASURING, measuring(true, T0))

        transport.connected.value = false

        assertFalse(link.isWatchMeasuring())
    }

    @Test
    fun `늦게 도착한 옛 메시지는 새 값을 덮지 않는다`() = runTest {
        val link = link()
        transport.receive(WearPaths.WATCH_MEASURING, measuring(false, T0.plusSeconds(10)))

        transport.receive(WearPaths.WATCH_MEASURING, measuring(true, T0))

        assertFalse(link.isWatchMeasuring())
    }

    @Test
    fun `해독할 수 없는 메시지는 버린다`() = runTest {
        val link = link()

        transport.receive(WearPaths.WATCH_MEASURING, "{".toByteArray())

        assertFalse(link.isWatchMeasuring())
    }

    private fun measuring(isMeasuring: Boolean, at: Instant): ByteArray =
        WatchMeasuringMessage(isMeasuring, at.toEpochMilli()).encode()

    private fun TestScope.link(): WatchLink {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        return WatchLink(transport, NoSettings, scope, clock = clock)
    }

    /** 시험이 바꾸는 시계(폰이 받은 시각 기준). */
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private object NoSettings : SettingsRepository {
        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

        override fun locationConsented() = false

        override fun heartRateConsented() = false

        override fun watchLinked() = true

        override fun courseMapShared() = false

        override fun birthYear(): Int? = null

        override fun guidanceSettings() = SettingsSnapshot.SAFE.guidanceSettings

        override fun isGuest(): Boolean = false

        override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-11-01T01:00:00Z")
    }
}
