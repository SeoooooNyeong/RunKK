package com.a207.runkk.watch.link

import com.a207.runkk.watch.alert.ProximityAlertPlayer
import com.a207.runkk.watch.heart.HeartRateCollector
import com.a207.runkk.watch.testing.FakeAlertOutput
import com.a207.runkk.watch.testing.FakeHeartRateSensor
import com.a207.runkk.watch.testing.FakeSensorPermission
import com.a207.runkk.watch.testing.FakeWearDataLayer
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.SchedulerMonotonicClock
import com.a207.runkk.watch.testing.T0
import com.a207.runkk.watch.testing.alertPlayer
import com.a207.runkk.watch.testing.failed
import com.a207.runkk.watch.testing.ok
import com.a207.runkk.wear.contract.EndRequestMessage
import com.a207.runkk.wear.contract.HeartRateBatchMessage
import com.a207.runkk.wear.contract.HeartRateBatchMessage.Sample
import com.a207.runkk.wear.contract.HeartRateBatchMessage.Status
import com.a207.runkk.wear.contract.LeadChangeMessage
import com.a207.runkk.wear.contract.PauseRequestMessage
import com.a207.runkk.wear.contract.ProximityMessage
import com.a207.runkk.wear.contract.StartRequestMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WearPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 6.1.15 PhoneLink: 폰 → 워치 표시, 워치 → 폰 시작 요청·심박 묶음, 수집 금지 플래그, 재연결(D-20, NFR-023, NFR-032). */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneLinkTest {
    private val dataLayer = FakeWearDataLayer()
    private val sensor = FakeHeartRateSensor()
    private val permission = FakeSensorPermission(isGranted = true)

    private class Harness(
        val link: PhoneLink,
        val collector: HeartRateCollector,
        val displays: List<WatchDisplayMessage>,
        val player: ProximityAlertPlayer,
        val alerts: FakeAlertOutput
    )

    private fun TestScope.harness(): Harness {
        val collector = HeartRateCollector(sensor, permission, backgroundScope)
        val alerts = FakeAlertOutput(SchedulerMonotonicClock(testScheduler))
        val player = alertPlayer(alerts)
        val link = PhoneLink(dataLayer, collector, player, SchedulerClock(testScheduler), backgroundScope)
        val displays = mutableListOf<WatchDisplayMessage>()
        backgroundScope.launch { link.displays.collect { displays += it } }
        link.start()
        runCurrent()
        return Harness(link, collector, displays, player, alerts)
    }

    private fun display(
        running: Boolean = true,
        allowed: Boolean = true,
        notice: String? = null,
        pausable: Boolean = false,
        paused: Boolean = false
    ) = WatchDisplayMessage(
        isRunning = running,
        elapsedSeconds = 60,
        distanceMeters = 200,
        currentPaceSecondsPerKm = 300,
        heartRateBpm = 150,
        planDistanceMeters = 5_000,
        planPaceSecondsPerKm = 330,
        isHeartRateCollectionAllowed = allowed,
        notice = notice,
        isPaused = paused,
        isPausable = pausable
    )

    private fun TestScope.receive(message: WatchDisplayMessage) {
        dataLayer.incoming.tryEmit(WearMessage(WearPaths.WATCH_DISPLAY, message.encode()))
        runCurrent()
    }

    private fun deliveredSamples(): List<Sample> =
        dataLayer.deliveredItems.flatMap { HeartRateBatchMessage.decode(it.second).samples }

    @Test
    fun `폰이 보낸 표시 정보를 wear contract 형식으로 해독해 displays로 내보낸다`() = runTest {
        val h = harness()
        val message = display(notice = "폰에서 러닝 화면을 여세요")

        receive(message)

        assertEquals(listOf(message), h.displays)
    }

    @Test
    fun `다른 경로의 메시지와 깨진 본문은 무시한다`() = runTest {
        val h = harness()

        dataLayer.incoming.tryEmit(WearMessage("/other", display().encode()))
        dataLayer.incoming.tryEmit(WearMessage(WearPaths.WATCH_DISPLAY, "not json".toByteArray()))
        runCurrent()

        assertTrue(h.displays.isEmpty())
    }

    @Test
    fun `러닝 중이고 수집이 허용되면 심박 수집을 시작한다`() = runTest {
        val h = harness()

        receive(display(running = true, allowed = true))

        assertEquals(HeartRateCollector.State.COLLECTING, h.collector.state.value)
        assertEquals(1, sensor.subscribers)
    }

    @Test
    fun `러닝 중이어도 수집 금지면 심박을 모으지 않는다`() = runTest {
        val h = harness()

        receive(display(running = true, allowed = false))

        assertEquals(HeartRateCollector.State.BLOCKED, h.collector.state.value)
        assertEquals(0, sensor.subscribers)
    }

    @Test
    fun `러닝 전 시작 화면 표시에서는 수집하지 않는다`() = runTest {
        val h = harness()

        receive(display(running = false, allowed = true))

        assertEquals(HeartRateCollector.State.STOPPED, h.collector.state.value)
        assertEquals(0, sensor.subscribers)
    }

    @Test
    fun `수집 금지 플래그가 오면 즉시 멈추고 아직 보내지 않은 심박을 버린다`() = runTest {
        val h = harness()
        receive(display(allowed = true))
        sensor.emit(ok(T0 + 1_000, 140))
        sensor.emit(ok(T0 + 2_000, 141))
        runCurrent()

        receive(display(allowed = false))
        advanceTimeBy(PhoneLink.FLUSH_INTERVAL_MILLIS * 2)
        runCurrent()

        assertEquals(0, sensor.subscribers)
        assertEquals(HeartRateCollector.State.BLOCKED, h.collector.state.value)
        assertTrue(dataLayer.putItems.isEmpty())
        assertNull(h.link.latestHeartRate.value)
    }

    @Test
    fun `심박을 묶음 크기만큼 모으면 DataClient로 wear contract 형식의 묶음을 보낸다`() = runTest {
        harness()
        receive(display())

        val measured = (0 until PhoneLink.BATCH_SIZE).map { i ->
            if (i == 3) failed(T0 + i * 1_000L) else ok(T0 + i * 1_000L, 130 + i)
        }
        measured.forEach { sensor.emit(it) }
        runCurrent()

        assertEquals(1, dataLayer.putItems.size)
        val (path, bytes) = dataLayer.putItems.single()
        assertTrue(path.startsWith(WearPaths.HEART_RATE_BATCH + "/"))
        val expected = measured.map {
            Sample(
                it.measuredAtEpochMillis,
                it.bpm,
                if (it.status == com.a207.runkk.watch.heart.WatchHeartRate.Status.OK) Status.OK else Status.FAILED
            )
        }
        assertEquals(HeartRateBatchMessage(expected), HeartRateBatchMessage.decode(bytes))
    }

    @Test
    fun `묶음 크기가 안 되어도 주기마다 남은 심박을 보낸다`() = runTest {
        harness()
        receive(display())
        sensor.emit(ok(T0 + 1_000, 140))
        runCurrent()
        assertTrue(dataLayer.putItems.isEmpty())

        advanceTimeBy(PhoneLink.FLUSH_INTERVAL_MILLIS + 1)
        runCurrent()

        assertEquals(listOf(Sample(T0 + 1_000, 140, Status.OK)), deliveredSamples())
    }

    @Test
    fun `러닝이 끝났다는 표시가 오면 수집을 멈추고 남은 심박을 바로 보낸다`() = runTest {
        val h = harness()
        receive(display(running = true))
        sensor.emit(ok(T0 + 1_000, 140))
        runCurrent()

        receive(display(running = false))

        assertEquals(HeartRateCollector.State.STOPPED, h.collector.state.value)
        assertEquals(0, sensor.subscribers)
        assertEquals(listOf(Sample(T0 + 1_000, 140, Status.OK)), deliveredSamples())
    }

    @Test
    fun `묶음마다 다른 경로에 넣어 끊긴 동안 쌓인 묶음이 서로 덮어쓰지 않는다`() = runTest {
        harness()
        receive(display())

        repeat(2) { round ->
            repeat(PhoneLink.BATCH_SIZE) { sensor.emit(ok(T0 + round * 100_000L + it * 1_000L, 150)) }
            runCurrent()
        }

        assertEquals(2, dataLayer.putItems.size)
        assertEquals(2, dataLayer.putItems.map { it.first }.toSet().size)
    }

    @Test
    fun `저장에 실패한 묶음은 버리지 않고 다음 전송에 함께 보낸다`() = runTest {
        harness()
        receive(display())
        dataLayer.failPutData = true
        sensor.emit(ok(T0 + 1_000, 140))
        advanceTimeBy(PhoneLink.FLUSH_INTERVAL_MILLIS + 1)
        runCurrent()
        assertTrue(dataLayer.putItems.isEmpty())

        dataLayer.failPutData = false
        sensor.emit(ok(T0 + 7_000, 142))
        advanceTimeBy(PhoneLink.FLUSH_INTERVAL_MILLIS + 1)
        runCurrent()

        assertEquals(
            listOf(Sample(T0 + 1_000, 140, Status.OK), Sample(T0 + 7_000, 142, Status.OK)),
            deliveredSamples()
        )
    }

    @Test
    fun `연결이 끊긴 동안에도 수집을 이어 가고 재연결 뒤 끊긴 동안의 심박이 모두 폰에 닿는다`() = runTest {
        val h = harness()
        receive(display())
        val measured = mutableListOf<Sample>()

        repeat(5) { round ->
            dataLayer.nodes = emptyList()
            advanceTimeBy(PhoneLink.CONNECTION_POLL_MILLIS + 1)
            runCurrent()
            assertFalse(h.link.phoneConnected.value)
            assertEquals(HeartRateCollector.State.COLLECTING, h.collector.state.value)

            repeat(7) { i ->
                val at = T0 + round * 100_000L + i * 1_000L
                sensor.emit(ok(at, 150 + i))
                measured += Sample(at, 150 + i, Status.OK)
            }
            advanceTimeBy(PhoneLink.FLUSH_INTERVAL_MILLIS + 1)
            runCurrent()

            dataLayer.nodes = listOf("phone")
            advanceTimeBy(PhoneLink.CONNECTION_POLL_MILLIS + 1)
            runCurrent()
            assertTrue(h.link.phoneConnected.value)
        }

        assertEquals(measured, deliveredSamples().sortedBy { it.measuredAtEpochMillis })
    }

    @Test
    fun `러닝 중 표시가 오래 끊기면 배터리를 위해 수집을 멈춘다`() = runTest {
        val h = harness()
        receive(display(running = true))

        advanceTimeBy(PhoneLink.MAX_SILENT_RUN_MILLIS + 1)
        runCurrent()

        assertEquals(HeartRateCollector.State.STOPPED, h.collector.state.value)
        assertEquals(0, sensor.subscribers)
    }

    @Test
    fun `표시가 계속 오면 수집을 멈추지 않는다`() = runTest {
        val h = harness()
        receive(display(running = true))

        repeat(4) {
            advanceTimeBy(PhoneLink.MAX_SILENT_RUN_MILLIS / 2)
            receive(display(running = true))
        }

        assertEquals(HeartRateCollector.State.COLLECTING, h.collector.state.value)
    }

    @Test
    fun `시작 요청은 연결된 폰에 wear contract 형식으로 보낸다`() = runTest {
        val h = harness()
        advanceTimeBy(1_234)

        val sent = h.link.sendStartRequest()

        assertTrue(sent)
        val (node, path, bytes) = dataLayer.sentMessages.single()
        assertEquals("phone", node)
        assertEquals(WearPaths.START_REQUEST, path)
        assertEquals(StartRequestMessage(T0 + 1_234), StartRequestMessage.decode(bytes))
    }

    @Test
    fun `종료 요청은 연결된 폰에 END_REQUEST 경로로 보낸다`() = runTest {
        val h = harness()
        advanceTimeBy(500)

        assertTrue(h.link.sendEndRequest())

        val (node, path, bytes) = dataLayer.sentMessages.single()
        assertEquals("phone", node)
        assertEquals(WearPaths.END_REQUEST, path)
        assertEquals(EndRequestMessage(T0 + 500), EndRequestMessage.decode(bytes))
    }

    @Test
    fun `폰과 연결되지 않았거나 전송에 실패하면 종료 요청은 거짓이다`() = runTest {
        val h = harness()
        dataLayer.nodes = emptyList()
        assertFalse(h.link.sendEndRequest())

        dataLayer.nodes = listOf("phone")
        dataLayer.failSendMessage = true
        assertFalse(h.link.sendEndRequest())
        assertTrue(dataLayer.sentMessages.isEmpty())
    }

    @Test
    fun `폰과 연결되지 않았으면 시작 요청을 보내지 않고 거짓을 돌려준다`() = runTest {
        val h = harness()
        dataLayer.nodes = emptyList()

        val sent = h.link.sendStartRequest()

        assertFalse(sent)
        assertFalse(h.link.phoneConnected.value)
        assertTrue(dataLayer.sentMessages.isEmpty())
    }

    @Test
    fun `메시지 전송에 실패하면 거짓을 돌려준다`() = runTest {
        val h = harness()
        dataLayer.failSendMessage = true

        assertFalse(h.link.sendStartRequest())
    }

    @Test
    fun `연결 상태는 주기적으로 다시 확인한다`() = runTest {
        val h = harness()
        assertTrue(h.link.phoneConnected.value)

        dataLayer.nodes = emptyList()
        advanceTimeBy(PhoneLink.CONNECTION_POLL_MILLIS + 1)
        runCurrent()
        assertFalse(h.link.phoneConnected.value)

        dataLayer.nodes = listOf("phone")
        advanceTimeBy(PhoneLink.CONNECTION_POLL_MILLIS + 1)
        runCurrent()
        assertTrue(h.link.phoneConnected.value)
    }

    @Test
    fun `최근 정상 심박만 latestHeartRate로 알린다`() = runTest {
        val h = harness()
        receive(display())

        sensor.emit(ok(T0 + 1_000, 140))
        sensor.emit(failed(T0 + 2_000))
        runCurrent()

        assertEquals(Sample(T0 + 1_000, 140, Status.OK), h.link.latestHeartRate.value)
    }

    @Test
    fun `OS 권한이 없으면 권한 필요를 알리고 권한을 받은 뒤 수집한다`() = runTest {
        permission.isGranted = false
        val h = harness()
        receive(display())
        assertTrue(h.link.heartRatePermissionNeeded.value)
        assertEquals(0, sensor.subscribers)

        permission.isGranted = true
        h.link.onHeartRatePermissionChanged()
        runCurrent()

        assertFalse(h.link.heartRatePermissionNeeded.value)
        assertEquals(1, sensor.subscribers)
    }

    @Test
    fun `전송이 늦어져도 심박 수신과 다음 메시지 처리는 막히지 않는다`() = runTest {
        val h = harness()
        receive(display())
        dataLayer.putGate = CompletableDeferred()
        repeat(PhoneLink.BATCH_SIZE) { sensor.emit(ok(T0 + it * 1_000L, 140)) }
        runCurrent()

        sensor.emit(ok(T0 + 50_000, 171))
        runCurrent()
        assertEquals(Sample(T0 + 50_000, 171, Status.OK), h.link.latestHeartRate.value)

        receive(display(allowed = false))
        assertEquals(0, sensor.subscribers)
        assertEquals(HeartRateCollector.State.BLOCKED, h.collector.state.value)
    }

    @Test
    fun `철회된 뒤 다시 허용되면 철회 전에 잰 심박은 보내지 않는다`() = runTest {
        harness()
        receive(display(allowed = true))
        advanceTimeBy(10_000)
        receive(display(allowed = false))
        advanceTimeBy(10_000)
        receive(display(allowed = true))

        sensor.emit(ok(T0 + 5_000, 140))
        sensor.emit(ok(T0 + testScheduler.currentTime, 150))
        advanceTimeBy(PhoneLink.FLUSH_INTERVAL_MILLIS + 1)
        runCurrent()

        assertEquals(listOf(Sample(T0 + 20_000, 150, Status.OK)), deliveredSamples())
    }

    @Test
    fun `보내지 못한 심박은 상한까지만 두고 오래된 것부터 버린다`() = runTest {
        harness()
        receive(display())
        dataLayer.failPutData = true
        val total = PhoneLink.MAX_PENDING_SAMPLES + 5
        repeat(total) { sensor.emit(ok(T0 + it.toLong(), 140)) }
        runCurrent()

        dataLayer.failPutData = false
        advanceTimeBy(PhoneLink.FLUSH_INTERVAL_MILLIS + 1)
        runCurrent()

        val delivered = deliveredSamples()
        assertEquals(PhoneLink.MAX_PENDING_SAMPLES, delivered.size)
        assertEquals(T0 + 5, delivered.minOf { it.measuredAtEpochMillis })
    }

    @Test
    fun `표시 정보를 받은 시각을 알린다`() = runTest {
        val h = harness()
        assertNull(h.link.lastDisplayAtMillis.value)
        advanceTimeBy(2_000)

        receive(display())

        assertEquals(T0 + 2_000, h.link.lastDisplayAtMillis.value)
    }

    @Test
    fun `밀린 심박은 묶음 상한 크기로 나눠 보내고 하나도 빠뜨리지 않는다`() = runTest {
        harness()
        receive(display())
        dataLayer.failPutData = true
        val total = PhoneLink.MAX_BATCH_SAMPLES * 2 + 7
        repeat(total) { sensor.emit(ok(T0 + it.toLong(), 140)) }
        runCurrent()

        dataLayer.failPutData = false
        advanceTimeBy(PhoneLink.FLUSH_INTERVAL_MILLIS + 1)
        runCurrent()

        val sizes = dataLayer.putItems.map { HeartRateBatchMessage.decode(it.second).samples.size }
        assertTrue(sizes.all { it <= PhoneLink.MAX_BATCH_SAMPLES })
        assertEquals(total, deliveredSamples().map { it.measuredAtEpochMillis }.toSet().size)
    }

    @Test
    fun `시작할 때 이전 프로세스가 남긴 운동 세션을 한 번만 정리한다`() = runTest {
        val h = harness()

        h.link.start()
        runCurrent()

        assertEquals(1, sensor.leftoverChecks)
    }

    private fun TestScope.proximity(active: Boolean = true, intervalMillis: Long = 1_000) = ProximityMessage(
        isActive = active,
        gapMeters = if (active) 30 else 0,
        intervalMillis = if (active) intervalMillis else 0,
        isAhead = true,
        sentAtEpochMillis = T0 + testScheduler.currentTime
    )

    private fun TestScope.receiveRaw(path: String, bytes: ByteArray) {
        dataLayer.incoming.tryEmit(WearMessage(path, bytes))
        runCurrent()
    }

    @Test
    fun `TC-6568 a 일시정지할 수 있는 표시를 받았으면 PAUSE_REQUEST 경로로 요청을 보낸다`() = runTest {
        val h = harness()
        receive(display(pausable = true))
        advanceTimeBy(700)

        assertTrue(h.link.sendPauseRequest(PauseRequestMessage.Action.PAUSE))

        val (node, path, bytes) = dataLayer.sentMessages.single()
        assertEquals("phone", node)
        assertEquals(WearPaths.PAUSE_REQUEST, path)
        assertEquals(
            PauseRequestMessage(PauseRequestMessage.Action.PAUSE, T0 + 700),
            PauseRequestMessage.decode(bytes)
        )
    }

    @Test
    fun `TC-6568 b 마지막 표시가 일시정지할 수 없으면 보내지 않는다`() = runTest {
        val h = harness()
        receive(display(pausable = true))
        receive(display(pausable = false))

        assertFalse(h.link.sendPauseRequest(PauseRequestMessage.Action.PAUSE))
        assertFalse(h.link.sendPauseRequest(PauseRequestMessage.Action.RESUME))

        assertTrue(dataLayer.sentMessages.isEmpty())
    }

    @Test
    fun `TC-6568 c 받은 표시가 없으면 보내지 않는다`() = runTest {
        val h = harness()

        assertFalse(h.link.sendPauseRequest(PauseRequestMessage.Action.PAUSE))

        assertTrue(dataLayer.sentMessages.isEmpty())
    }

    @Test
    fun `TC-6863 paused·pausable이 없는 옛 폰 표시를 받으면 일시정지 요청을 보내지 않는다`() = runTest {
        val h = harness()
        val old = """{"running":true,"elapsedSeconds":10,"distanceMeters":30,"heartRateCollectionAllowed":true}"""
        receiveRaw(WearPaths.WATCH_DISPLAY, old.toByteArray())

        assertFalse(h.displays.single().isPausable)
        assertFalse(h.link.sendPauseRequest(PauseRequestMessage.Action.PAUSE))
        assertTrue(dataLayer.sentMessages.isEmpty())
    }

    @Test
    fun `재개 요청은 RESUME으로 보내고 연결이 없으면 거짓이다`() = runTest {
        val h = harness()
        receive(display(pausable = true, paused = true))

        assertTrue(h.link.sendPauseRequest(PauseRequestMessage.Action.RESUME))
        assertEquals(
            PauseRequestMessage.Action.RESUME,
            PauseRequestMessage.decode(dataLayer.sentMessages.single().third).action
        )

        dataLayer.nodes = emptyList()
        assertFalse(h.link.sendPauseRequest(PauseRequestMessage.Action.RESUME))
    }

    @Test
    fun `러닝 중 PROXIMITY 메시지를 받으면 근접 알림을 재생한다`() = runTest {
        val h = harness()
        receive(display(running = true))

        receiveRaw(WearPaths.PROXIMITY, proximity(intervalMillis = 500).encode())
        advanceTimeBy(1_100)
        runCurrent()

        assertTrue(h.player.isPlaying.value)
        assertEquals(listOf(0L, 500L, 1_000L), h.alerts.beepTimes())
    }

    @Test
    fun `러닝 중 LEAD_CHANGE 메시지를 받으면 1초 긴 진동을 낸다`() = runTest {
        val h = harness()
        receive(display(running = true))

        receiveRaw(WearPaths.LEAD_CHANGE, LeadChangeMessage(LeadChangeMessage.Kind.OVERTOOK, T0).encode())

        assertEquals(listOf(0L), h.alerts.longVibrationTimes(ProximityAlertPlayer.LEAD_CHANGE_VIBRATION_MILLIS))
    }

    @Test
    fun `TC-6565 러닝이 끝났다는 표시를 받으면 근접 알림을 멈춘다`() = runTest {
        val h = harness()
        receive(display(running = true))
        receiveRaw(WearPaths.PROXIMITY, proximity(intervalMillis = 300).encode())
        assertTrue(h.player.isPlaying.value)

        receive(display(running = false))

        assertFalse(h.player.isPlaying.value)
    }

    @Test
    fun `러닝 중이 아닐 때 온 근접·추월 메시지는 버린다(늦게 도착한 메시지)`() = runTest {
        val h = harness()
        receive(display(running = false))

        receiveRaw(WearPaths.PROXIMITY, proximity().encode())
        receiveRaw(WearPaths.LEAD_CHANGE, LeadChangeMessage(LeadChangeMessage.Kind.OVERTAKEN, T0).encode())
        advanceTimeBy(5_000)
        runCurrent()

        assertFalse(h.player.isPlaying.value)
        assertTrue(h.alerts.events.isEmpty())
    }

    @Test
    fun `TC-6571 모르는 경로와 깨진 근접·추월 본문은 버리고 예외 없이 다음 메시지를 처리한다`() = runTest {
        val h = harness()
        receive(display(running = true))

        receiveRaw("/runkk/unknown", proximity().encode())
        receiveRaw(WearPaths.PROXIMITY, "not json".toByteArray())
        receiveRaw(WearPaths.LEAD_CHANGE, """{"kind":"TIED","atEpochMillis":1}""".toByteArray())
        assertTrue(h.alerts.events.isEmpty())
        assertFalse(h.player.isPlaying.value)

        receiveRaw(WearPaths.PROXIMITY, proximity().encode())
        assertTrue(h.player.isPlaying.value)
    }

    @Test
    fun `러닝 중이 아닌 표시면 pausable이어도 일시정지 요청을 보내지 않는다`() = runTest {
        val h = harness()
        receive(display(running = false, pausable = true))

        assertFalse(h.link.sendPauseRequest(PauseRequestMessage.Action.PAUSE))
        assertTrue(dataLayer.sentMessages.isEmpty())
    }

    @Test
    fun `러닝 중 켜진 근접 알림은 화면용 proximity로도 내보내고 꺼짐이나 러닝 끝에 지운다`() = runTest {
        val h = harness()
        receive(display(running = true))

        receiveRaw(WearPaths.PROXIMITY, proximity(intervalMillis = 800).encode())
        assertEquals(
            PhoneProximity(30, isAhead = true, intervalMillis = 800, receivedAtMillis = T0),
            h.link.proximity.value
        )

        receiveRaw(WearPaths.PROXIMITY, proximity(active = false).encode())
        assertNull(h.link.proximity.value)

        receiveRaw(WearPaths.PROXIMITY, proximity().encode())
        receive(display(running = false))
        assertNull(h.link.proximity.value)
    }

    @Test
    fun `러닝 중이 아닐 때 온 근접 알림은 화면에도 내보내지 않는다`() = runTest {
        val h = harness()
        receive(display(running = false))

        receiveRaw(WearPaths.PROXIMITY, proximity().encode())

        assertNull(h.link.proximity.value)
    }

    @Test
    fun `다시 연결은 연결 상태를 바로 다시 확인한다`() = runTest {
        val h = harness()
        dataLayer.nodes = emptyList()
        assertFalse(h.link.recheckConnection())
        assertFalse(h.link.phoneConnected.value)

        dataLayer.nodes = listOf("phone")
        assertTrue(h.link.recheckConnection())
        assertTrue(h.link.phoneConnected.value)
    }
}
