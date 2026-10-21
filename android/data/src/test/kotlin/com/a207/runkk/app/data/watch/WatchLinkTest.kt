package com.a207.runkk.app.data.watch

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.wear.contract.EndRequestMessage
import com.a207.runkk.wear.contract.HeartRateBatchMessage
import com.a207.runkk.wear.contract.StartRequestMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WearPaths
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** WatchLink(14장 단위(앱 데이터)): 동의 철회 즉시 수집 금지 플래그(NFR-023, H-19), 실패 표본·금지 중 심박 버림, 시작 요청. */
class WatchLinkTest {
    private val transport = FakeTransport()
    private val settings = FakeSettings(CONSENTED)

    @Test
    fun `표시 정보에 심박 수집 허용 여부를 실어 워치로 보낸다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())

        link.publish(DISPLAY)

        val sent = transport.displays().last()
        assertEquals(
            WatchDisplayMessage(
                running = true,
                elapsedSeconds = 605,
                distanceMeters = 2_012,
                currentPaceSecondsPerKm = 330,
                heartRateBpm = 151,
                planDistanceMeters = 5_000,
                planPaceSecondsPerKm = 360,
                heartRateCollectionAllowed = true,
                notice = null
            ),
            sent
        )
    }

    @Test
    fun `심박 동의를 철회하면 바로 수집 금지 플래그를 다시 보낸다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        link.publish(DISPLAY)
        val before = transport.displays().size

        settings.emit(CONSENTED.copy(heartRateConsented = false))

        val after = transport.displays()
        assertEquals(before + 1, after.size, "철회 알림 한 번에 한 번 다시 보낸다")
        assertFalse(after.last().heartRateCollectionAllowed)
        assertEquals(2_012, after.last().distanceMeters, "마지막 표시 정보를 그대로 다시 보낸다")
    }

    @Test
    fun `워치 연동을 끄면 표시 정보가 없어도 금지 플래그를 보낸다`() = runTest {
        WatchLink(transport, settings, linkScope())

        settings.emit(CONSENTED.copy(watchLinked = false))

        val last = transport.displays().last()
        assertFalse(last.running)
        assertFalse(last.heartRateCollectionAllowed)
    }

    @Test
    fun `실패 표본은 거르고 검증된 심박만 넘긴다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        val received = mutableListOf<List<HeartRateSample>>()
        linkScope().launch { link.heartRates.collect { received += it } }

        transport.receive(
            WearPaths.HEART_RATE_BATCH,
            HeartRateBatchMessage(
                listOf(
                    HeartRateBatchMessage.Sample(T0.toEpochMilli(), 140, HeartRateBatchMessage.Status.OK),
                    HeartRateBatchMessage.Sample(
                        T0.plusSeconds(1).toEpochMilli(),
                        0,
                        HeartRateBatchMessage.Status.FAILED
                    )
                )
            ).encode()
        )

        assertEquals(listOf(listOf(HeartRateSample(T0, 140))), received)
    }

    @Test
    fun `수집이 금지된 동안 온 심박은 버린다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        val received = mutableListOf<List<HeartRateSample>>()
        linkScope().launch { link.heartRates.collect { received += it } }
        settings.emit(CONSENTED.copy(heartRateConsented = false))

        transport.receive(
            "${WearPaths.HEART_RATE_BATCH}/42",
            HeartRateBatchMessage(
                listOf(HeartRateBatchMessage.Sample(T0.toEpochMilli(), 140, HeartRateBatchMessage.Status.OK))
            ).encode()
        )

        assertTrue(received.isEmpty())
    }

    @Test
    fun `워치 시작 요청을 넘기고 연결 상태를 알려 준다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        var requests = 0
        linkScope().launch { link.startRequests.collect { requests++ } }

        transport.receive(WearPaths.START_REQUEST, StartRequestMessage(T0.toEpochMilli()).encode())
        transport.receive(WearPaths.START_REQUEST, byteArrayOf(1, 2, 3))

        assertEquals(1, requests, "해독할 수 없는 요청은 버린다")
        transport.connected.value = true
        assertTrue(link.isConnected())
    }

    /** 시험이 끝나면 함께 취소되고, 구독·방출이 바로 도는 범위. */
    private fun TestScope.linkScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private class FakeTransport : WatchLink.Transport {
        val sent = mutableListOf<Pair<String, ByteArray>>()
        override val connected = MutableStateFlow(false)
        override val incoming = MutableSharedFlow<WatchLink.Incoming>(extraBufferCapacity = 16)

        override fun send(path: String, payload: ByteArray) {
            sent += path to payload
        }

        fun receive(path: String, payload: ByteArray) {
            check(incoming.tryEmit(WatchLink.Incoming(path, payload)))
        }

        fun displays(): List<WatchDisplayMessage> =
            sent.filter { it.first == WearPaths.WATCH_DISPLAY }.map { WatchDisplayMessage.decode(it.second) }
    }

    private class FakeSettings(initial: SettingsSnapshot) : SettingsRepository {
        private val state = MutableStateFlow(initial)

        fun emit(s: SettingsSnapshot) {
            state.value = s
        }

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

        override fun locationConsented() = state.value.locationConsented

        override fun heartRateConsented() = state.value.heartRateConsented

        override fun watchLinked() = state.value.watchLinked

        override fun courseMapShared() = state.value.courseMapShared

        override fun birthYear() = state.value.birthYear

        override fun guidanceSettings() = state.value.guidanceSettings

        override fun changes(): Flow<SettingsSnapshot> = state

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-10-20T01:00:00Z")
        val CONSENTED =
            SettingsSnapshot(
                true,
                true,
                watchLinked = true,
                courseMapShared = false,
                birthYear = 1990,
                GuidanceSettings.DEFAULT
            )
        val DISPLAY =
            WatchDisplay(
                running = true,
                elapsed = Duration.ofSeconds(605),
                distance = Distance(2_012),
                currentPace = Pace(330),
                heartRateBpm = 151,
                todayPlan = PlanSnapshot(Distance(5_000), Pace(360)),
                notice = null
            )
    }

    @Test
    fun `워치 종료 요청을 넘긴다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        var requests = 0
        linkScope().launch { link.endRequests.collect { requests++ } }

        transport.receive(WearPaths.END_REQUEST, EndRequestMessage(T0.toEpochMilli()).encode())

        assertEquals(1, requests)
    }

    @Test
    fun `같은 측정 시각의 심박이 다시 와도 한 번만 넘긴다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        val received = mutableListOf<List<HeartRateSample>>()
        linkScope().launch { link.heartRates.collect { received += it } }
        val first = HeartRateBatchMessage.Sample(T0.toEpochMilli(), 140, HeartRateBatchMessage.Status.OK)
        val second = HeartRateBatchMessage.Sample(
            T0.plusSeconds(1).toEpochMilli(),
            141,
            HeartRateBatchMessage.Status.OK
        )

        transport.receive(
            "${WearPaths.HEART_RATE_BATCH}/${first.measuredAtEpochMillis}-0",
            HeartRateBatchMessage(listOf(first)).encode()
        )
        transport.receive(
            "${WearPaths.HEART_RATE_BATCH}/${first.measuredAtEpochMillis}-1",
            HeartRateBatchMessage(listOf(first, second)).encode()
        )

        assertEquals(
            listOf(listOf(HeartRateSample(T0, 140)), listOf(HeartRateSample(T0.plusSeconds(1), 141))),
            received
        )
    }

    @Test
    fun `워치가 연결되면 마지막 표시 정보를 다시 보낸다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        link.publish(DISPLAY.copy(running = false))
        val before = transport.displays().size

        transport.connected.value = true

        assertEquals(before + 1, transport.displays().size)
        assertTrue(transport.displays().last().heartRateCollectionAllowed)
    }
}
