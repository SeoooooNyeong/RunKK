package com.a207.runkk.app.data.watch

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.run.WatchNotice
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.wear.contract.EndRequestMessage
import com.a207.runkk.wear.contract.HeartRateBatchMessage
import com.a207.runkk.wear.contract.LeadChangeMessage
import com.a207.runkk.wear.contract.PauseRequestMessage
import com.a207.runkk.wear.contract.ProximityMessage
import com.a207.runkk.wear.contract.StartRequestMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WearPaths
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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
                isRunning = true,
                elapsedSeconds = 605,
                distanceMeters = 2_012,
                currentPaceSecondsPerKm = 330,
                heartRateBpm = 151,
                planDistanceMeters = 5_000,
                planPaceSecondsPerKm = 360,
                isHeartRateCollectionAllowed = true,
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

        settings.emit(CONSENTED.copy(isHeartRateConsented = false))

        val after = transport.displays()
        assertEquals(before + 1, after.size, "철회 알림 한 번에 한 번 다시 보낸다")
        assertFalse(after.last().isHeartRateCollectionAllowed)
        assertEquals(2_012, after.last().distanceMeters, "마지막 표시 정보를 그대로 다시 보낸다")
    }

    @Test
    fun `워치 연동을 끄면 표시 정보가 없어도 금지 플래그를 보낸다`() = runTest {
        WatchLink(transport, settings, linkScope())

        settings.emit(CONSENTED.copy(isWatchLinked = false))

        val last = transport.displays().last()
        assertFalse(last.isRunning)
        assertFalse(last.isHeartRateCollectionAllowed)
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
        settings.emit(CONSENTED.copy(isHeartRateConsented = false))

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

    @Test
    fun `워치 일시정지·재개 요청을 넘기고 모르는 action은 버린다(D-157 (6))`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        val actions = mutableListOf<WatchGateway.PauseAction>()
        linkScope().launch { link.pauseRequests.collect { actions += it } }

        transport.receive(WearPaths.PAUSE_REQUEST, PauseRequestMessage(PauseRequestMessage.Action.PAUSE, 1).encode())
        transport.receive(WearPaths.PAUSE_REQUEST, """{"action":"STOP","requestedAtEpochMillis":2}""".toByteArray())
        transport.receive(WearPaths.PAUSE_REQUEST, PauseRequestMessage(PauseRequestMessage.Action.RESUME, 3).encode())

        assertEquals(listOf(WatchGateway.PauseAction.PAUSE, WatchGateway.PauseAction.RESUME), actions)
    }

    @Test
    fun `근접 알림을 ProximityMessage로 보내고 null이면 끔(0·0)을 보낸다`() = runTest {
        val clock = Clock.fixed(T0, ZoneOffset.UTC)
        val link = WatchLink(transport, settings, linkScope(), clock = clock)

        link.publishProximity(ProximityAlert(24, Duration.ofMillis(640), isAhead = true))
        link.publishProximity(null)

        val sent = transport.sent.filter { it.first == WearPaths.PROXIMITY }
        assertEquals(
            listOf(
                ProximityMessage(true, 24, 640, true, T0.toEpochMilli()),
                ProximityMessage(false, 0, 0, false, T0.toEpochMilli())
            ),
            sent.map { ProximityMessage.decode(it.second) }
        )
        val keys = Json.parseToJsonElement(sent.first().second.decodeToString()).jsonObject.keys
        assertTrue("active" in keys && "ahead" in keys && "isActive" !in keys, "$keys")
    }

    @Test
    fun `추월·역전만 LeadChangeMessage로 보낸다`() = runTest {
        val link = WatchLink(transport, settings, linkScope(), clock = Clock.fixed(T0, ZoneOffset.UTC))

        link.publishLeadChange(Guidance.Kind.OVERTAKEN)
        link.publishLeadChange(Guidance.Kind.SPLIT)

        assertEquals(
            listOf(LeadChangeMessage(LeadChangeMessage.Kind.OVERTAKEN, T0.toEpochMilli())),
            transport.sent.filter { it.first == WearPaths.LEAD_CHANGE }.map { LeadChangeMessage.decode(it.second) }
        )
    }

    @Test
    fun `TC-6401 표시 정보에 정지 중·정지 가능을 싣는다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())

        link.publish(DISPLAY.copy(isPaused = true, isPausable = true))

        val sent = transport.displays().last()
        assertTrue(sent.isPaused && sent.isPausable)
    }

    @Test
    fun `S15P31A207-278 측정 중 워치가 표시를 요청하면 진행 표시를 다시 보내고 그 워치 노드를 닿는 노드로 등록한다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        link.publish(DISPLAY)
        val before = transport.displays().size

        link.onDisplayRequested("watch-1")

        assertEquals(listOf("watch-1"), transport.reached)
        assertEquals(before + 1, transport.displays().size)
        assertEquals(transport.displays()[before - 1], transport.displays().last(), "마지막 진행 표시와 같다")
        assertTrue(transport.displays().last().isRunning)
    }

    @Test
    fun `S15P31A207-278 대기 중 워치가 표시를 요청하면 오늘 계획을 실은 대기 표시를 다시 보낸다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        link.publish(DISPLAY.copy(isRunning = false, elapsed = Duration.ZERO, distance = Distance(0)))

        link.onDisplayRequested("watch-1")

        val sent = transport.displays().last()
        assertFalse(sent.isRunning)
        assertEquals(5_000, sent.planDistanceMeters)
        assertEquals(360, sent.planPaceSecondsPerKm)
        assertTrue(sent.isHeartRateCollectionAllowed)
    }

    @Test
    fun `S15P31A207-278 폰 프로세스가 막 깨어나 보낸 표시가 없을 때 요청이 오면 기본 대기 표시를 보낸다`() = runTest {
        val link = WatchLink(transport, settings, linkScope())
        transport.sent.clear()

        link.onDisplayRequested("watch-1")

        assertEquals(
            WatchDisplayMessage(
                isRunning = false,
                elapsedSeconds = 0,
                distanceMeters = 0,
                isHeartRateCollectionAllowed = true
            ),
            transport.displays().single()
        )
    }

    @Test
    fun `S15P31A207-278 앱 안 리스너로 온 표시 요청으로는 다시 보내지 않는다(수신 서비스가 받는다, 중복 방지)`() = runTest {
        WatchLink(transport, settings, linkScope()).publish(DISPLAY)
        val before = transport.sent.size

        transport.receive(WearPaths.DISPLAY_REQUEST, ByteArray(0))

        assertEquals(before, transport.sent.size)
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

        override suspend fun sendNow(path: String, payload: ByteArray, nodeId: String?): Boolean = false

        override suspend fun putItem(path: String, payload: ByteArray): Boolean = false

        override suspend fun deleteItems(path: String): Boolean = false

        override suspend fun readItems(path: String): List<ByteArray> = emptyList()

        /** 닿는 노드로 등록한 워치 노드(차례대로). */
        val reached = mutableListOf<String>()

        override fun markReachable(nodeId: String) {
            reached += nodeId
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

        override fun locationConsented() = state.value.isLocationConsented

        override fun heartRateConsented() = state.value.isHeartRateConsented

        override fun watchLinked() = state.value.isWatchLinked

        override fun courseMapShared() = state.value.isCourseMapShared

        override fun birthYear() = state.value.birthYear

        override fun guidanceSettings() = state.value.guidanceSettings

        override fun isGuest(): Boolean = state.value.isGuest

        override fun changes(): Flow<SettingsSnapshot> = state

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-10-20T01:00:00Z")
        val CONSENTED =
            SettingsSnapshot(
                true,
                true,
                isWatchLinked = true,
                isCourseMapShared = false,
                birthYear = 1990,
                GuidanceSettings.DEFAULT
            )
        val DISPLAY =
            WatchDisplay(
                isRunning = true,
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
        link.publish(DISPLAY.copy(isRunning = false))
        val before = transport.displays().size

        transport.connected.value = true

        assertEquals(before + 1, transport.displays().size)
        assertTrue(transport.displays().last().isHeartRateCollectionAllowed)
    }

    @Test
    fun `M-3 안내 키는 문구로 바꿔 워치에 보낸다`() = runTest {
        val link = WatchLink(transport, settings, linkScope(), noticeText = { "문구:${it.name}" })

        link.publish(DISPLAY.copy(notice = WatchNotice.END_ON_PHONE))

        assertEquals("문구:END_ON_PHONE", transport.displays().last().notice)
    }
}
