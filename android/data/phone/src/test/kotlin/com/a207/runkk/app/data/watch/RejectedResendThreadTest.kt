package com.a207.runkk.app.data.watch

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.ResendRejectedRunsUseCase
import com.a207.runkk.app.domain.run.ResetRejectedRunsUseCase
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.UploadState
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.app.domain.sync.RunUploadRemote
import com.a207.runkk.app.domain.sync.UploadPendingRunsUseCase
import com.a207.runkk.app.domain.sync.UploadReceipt
import com.a207.runkk.wear.contract.RejectedCountMessage
import com.a207.runkk.wear.contract.WearPaths
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * 통합 T-52·T-54(test 16.5, FR-273): 설정의 "못 올린 기록 n건, 다시 보내기"가 부르는 ResendRejectedRunsUseCase부터 폰 보관함·전송,
 * WatchLink, 가짜 Data Layer 송수신까지 잇는다. 워치 쪽은 :wear를 쓰지 않고 wear-contract 메시지로 흉내 낸다(SimulatedWatch:
 * RESEND_REJECTED 항목을 받으면 자기 거부 기록을 되돌려 올리고 REJECTED_COUNT를 다시 알린다). 폰 보관함은 메모리 보관함이다.
 */
class RejectedResendThreadTest {
    private val recording = RecordingTransport()
    private val watch = SimulatedWatch()
    private val transport = WatchSideTransport(recording, watch)
    private val settings = FakeWatchSettings(SettingsSnapshot.SAFE.copy(isWatchLinked = true))
    private val pending = MemoryPendingRuns()
    private val scheduler = CountingScheduler()
    private val upload = UploadPendingRunsUseCase(pending, AcceptingRemote())
    private val clock = MutableClock(T0)

    @Test
    fun `TC-5551 폰에만 거부 기록 2건이면 줄은 2건이고 다시 보내면 폰 기록이 올라가 줄이 사라지며 워치 요청 항목이 1건 남는다`() = runTest {
        val useCase = useCase()
        val counts = collect(useCase)
        pending.save(rejected("p-1"))
        pending.save(rejected("p-2"))
        assertEquals(2, counts.last())

        assertEquals(Outcome.Success(2), useCase())
        assertEquals(1, scheduler.requests, "되돌린 뒤 전송을 깨운다")
        upload()

        assertEquals(emptyList(), pending.runs.value, "서버가 받은 기록은 보관함에서 지운다")
        assertEquals(0, counts.last())
        assertEquals(1, recording.of("put", WearPaths.RESEND_REJECTED).size)
    }

    @Test
    fun `TC-5552 워치에만 거부 기록 2건이면 줄은 2건이고 다시 보내면 워치가 되돌려 0건을 알려 줄이 사라진다`() = runTest {
        val useCase = useCase()
        val counts = collect(useCase)
        watch.report(rejected = 2)
        assertEquals(2, counts.last())

        clock.now = T0.plusSeconds(10)
        assertEquals(Outcome.Success(0), useCase(), "폰에는 되돌릴 기록이 없다")

        assertEquals(0, scheduler.requests, "폰에 되돌린 기록이 없으면 전송을 깨우지 않는다")
        assertEquals(1, watch.resendRequests)
        assertEquals(0, watch.rejected)
        assertEquals(listOf(0, 2, 0), counts)
    }

    @Test
    fun `TC-5553 워치가 끊긴 동안은 폰 건수만 세고 남긴 요청은 다시 연결되면 전달되어 워치가 되돌린다`() = runTest {
        val useCase = useCase()
        val counts = collect(useCase)
        pending.save(rejected("p-1"))
        watch.report(rejected = 1)
        assertEquals(2, counts.last())

        recording.connected.value = false
        assertEquals(1, counts.last(), "끊긴 동안 워치 건수는 0으로 센다")
        clock.now = T0.plusSeconds(10)
        useCase()
        upload()
        assertEquals(0, counts.last())
        assertEquals(0, watch.resendRequests, "끊긴 동안 워치는 요청을 받지 못한다")
        assertEquals(1, recording.of("put", WearPaths.RESEND_REJECTED).size, "요청은 항목으로 남는다")

        watch.reconnect()

        assertEquals(1, watch.resendRequests)
        assertEquals(0, watch.rejected)
        assertEquals(0, counts.last(), "다시 연결되면 워치가 알린 0건을 읽는다")
    }

    @Test
    fun `TC-5556 워치 거부 기록이 0에서 1, 3으로 늘면 줄 건수가 폰 건수에 더해 바뀐다`() = runTest {
        val useCase = useCase()
        val counts = collect(useCase)
        pending.save(rejected("p-1"))

        listOf(0, 1, 3).forEachIndexed { second, rejected ->
            clock.now = T0.plusSeconds(second.toLong())
            watch.report(rejected)
        }

        assertEquals(listOf(0, 1, 2, 4), counts)
    }

    private fun TestScope.useCase(): ResendRejectedRunsUseCase {
        val link = WatchLink(transport, settings, scope(), clock = clock)
        watch.attach(recording, clock)
        return ResendRejectedRunsUseCase(pending, ResetRejectedRunsUseCase(pending, scheduler), link)
    }

    private fun TestScope.collect(useCase: ResendRejectedRunsUseCase): List<Int> {
        val counts = mutableListOf<Int>()
        scope().launch { useCase.observeRejectedCount().collect { counts += it } }
        return counts
    }

    private fun TestScope.scope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun rejected(id: String) = CompletedRun(
        recordId = id,
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = T0,
        elapsed = Duration.ofMinutes(10),
        distance = Distance(2_000),
        route = emptyList(),
        unstable = emptyList(),
        heartRates = emptyList(),
        savedAt = T0,
        uploadState = UploadState.REJECTED,
        rejectedReason = "PAUSE_OUT_OF_RANGE"
    )

    /**
     * 워치 흉내: 연결되어 있을 때 RESEND_REJECTED 항목을 받으면 자기 거부 기록을 되돌려(서버가 받았다고 보고) 0건을 REJECTED_COUNT로
     * 알린다. 끊긴 동안 넣은 항목은 다시 연결될 때 받는다. 알린 건수 항목은 Data Layer에도 남긴다(폰이 다시 연결될 때 읽는다).
     */
    private class SimulatedWatch {
        var rejected = 0
        var resendRequests = 0
        private var missed = false
        private lateinit var transport: RecordingTransport
        private lateinit var clock: Clock

        fun attach(transport: RecordingTransport, clock: Clock) {
            this.transport = transport
            this.clock = clock
        }

        fun report(rejected: Int) {
            this.rejected = rejected
            val payload = RejectedCountMessage(rejected, clock.millis()).encode()
            transport.storedItems[WearPaths.REJECTED_COUNT] = listOf(payload)
            if (transport.connected.value) transport.receive(WearPaths.REJECTED_COUNT, payload)
        }

        fun onResendItem() {
            if (!transport.connected.value) {
                missed = true
                return
            }
            resendRequests++
            report(rejected = 0)
        }

        fun reconnect() {
            if (missed) {
                missed = false
                resendRequests++
                rejected = 0
                transport.storedItems[WearPaths.REJECTED_COUNT] =
                    listOf(RejectedCountMessage(0, clock.millis()).encode())
            }
            transport.connected.value = true
        }
    }

    /** 송수신은 RecordingTransport가 하고, RESEND_REJECTED 항목이 들어가면 워치 흉내에 알린다. */
    private class WatchSideTransport(private val inner: RecordingTransport, private val watch: SimulatedWatch) :
        WatchLink.Transport by inner {
        override suspend fun putItem(path: String, payload: ByteArray): Boolean {
            val put = inner.putItem(path, payload)
            if (put && path == WearPaths.RESEND_REJECTED) watch.onResendItem()
            return put
        }
    }

    private class CountingScheduler : UploadScheduler {
        var requests = 0

        override fun requestUpload() {
            requests++
        }
    }

    /** 서버 200: 모든 기록을 받는다. */
    private class AcceptingRemote : RunUploadRemote {
        override suspend fun upload(run: CompletedRun): Outcome<UploadReceipt> =
            Outcome.Success(UploadReceipt(run.recordId, isAlreadySaved = false))

        override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> =
            Outcome.Success(Unit)
    }

    /** 메모리 보관함. markRejected·resetRejected·observeRejectedCount는 포트의 기본 구현을 쓴다. */
    private class MemoryPendingRuns : PendingRunRepository {
        val runs = MutableStateFlow<List<CompletedRun>>(emptyList())

        override suspend fun save(run: CompletedRun): Outcome<Unit> {
            runs.value = runs.value.filterNot { it.recordId == run.recordId } + run
            return Outcome.Success(Unit)
        }

        override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(runs.value)

        override fun observePending(): Flow<List<CompletedRun>> = runs

        override suspend fun remove(recordId: String): Outcome<Unit> {
            runs.value = runs.value.filterNot { it.recordId == recordId }
            return Outcome.Success(Unit)
        }

        override suspend fun purgeRoutes(): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun purgeHeartRates(): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun needsReminder(now: Instant): Outcome<Boolean> = Outcome.Success(false)

        override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> =
            Outcome.Success(Unit)

        override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = Outcome.Success(emptyList())

        override suspend fun removeLateHeartRates(sent: LateHeartRates): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun clear(): Outcome<Unit> {
            runs.value = emptyList()
            return Outcome.Success(Unit)
        }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-11-02T01:00:00Z")
    }
}
