package com.a207.runkk.watch.link

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.UploadState
import com.a207.runkk.watch.testing.FakePendingRunRepository
import com.a207.runkk.watch.testing.FakeWearDataLayer
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.T0
import com.a207.runkk.wear.contract.RejectedCountMessage
import com.a207.runkk.wear.contract.WearPaths
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * W43(AN16-1): 워치는 자기 거부 기록(REJECTED) 건수를 DataClient 항목(REJECTED_COUNT)으로 폰에 알린다. 건수가 바뀔 때만 다시 넣고,
 * 항목은 연결이 끊긴 동안에도 남았다가 연결되면 폰에 간다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RejectedCountReporterTest {
    private val dataLayer = FakeWearDataLayer()
    private val runs = MutableStateFlow<List<CompletedRun>>(emptyList())
    private val pending = FakePendingRunRepository().apply { observed = runs }

    @Test
    fun `W43 시작하면 거부 건수를 알리고 건수가 바뀔 때만 다시 알린다`() = runTest {
        RejectedCountReporter(pending, dataLayer, SchedulerClock(testScheduler), backgroundScope).start()
        runCurrent()

        runs.value = listOf(rejected("a"), run("b"))
        runCurrent()
        advanceTimeBy(1_000)
        runs.value = listOf(rejected("a"), rejected("b"))
        runCurrent()
        runs.value = listOf(rejected("a"), rejected("b"), run("c"))
        runCurrent()
        runs.value = emptyList()
        runCurrent()

        assertEquals(listOf(0, 1, 2, 0), reported().map { it.count })
        assertEquals(listOf(0L, 0L, 1_000L, 1_000L), reported().map { it.atEpochMillis - T0 })
    }

    @Test
    fun `W43 거듭 시작해도 한 번만 알린다`() = runTest {
        val reporter = RejectedCountReporter(pending, dataLayer, SchedulerClock(testScheduler), backgroundScope)
        reporter.start()
        reporter.start()
        runCurrent()

        assertEquals(listOf(0), reported().map { it.count })
    }

    @Test
    fun `W43 폰이 끊겨 있어도 항목은 남았다가 연결되면 간다`() = runTest {
        dataLayer.nodes = emptyList()
        runs.value = listOf(rejected("a"))
        RejectedCountReporter(pending, dataLayer, SchedulerClock(testScheduler), backgroundScope).start()
        runCurrent()
        assertEquals(emptyList(), dataLayer.deliveredItems)

        dataLayer.nodes = listOf("phone")

        assertEquals(listOf(WearPaths.REJECTED_COUNT), dataLayer.deliveredItems.map { it.first })
    }

    private fun reported(): List<RejectedCountMessage> = dataLayer.putItems
        .filter { it.first == WearPaths.REJECTED_COUNT }
        .map { RejectedCountMessage.decode(it.second) }

    private fun rejected(id: String) = run(id).copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST")

    private fun run(id: String) = CompletedRun(
        recordId = id,
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = START,
        elapsed = Duration.ofMinutes(10),
        distance = Distance(2_000),
        route = emptyList(),
        unstable = emptyList(),
        heartRates = emptyList(),
        savedAt = START
    )

    private companion object {
        val START: Instant = Instant.parse("2026-11-02T00:00:00Z")
    }
}
