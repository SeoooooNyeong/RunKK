package com.a207.runkk.app.data.watch

import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.wear.contract.RejectedCountMessage
import com.a207.runkk.wear.contract.ResendRejectedMessage
import com.a207.runkk.wear.contract.WearPaths
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * W40·W43: 다시 보내기 요청은 DataClient 항목(RESEND_REJECTED)으로 남겨 워치가 연결되면 받게 하고(AN16-2), 워치가 알린 자기 거부 기록
 * 건수(REJECTED_COUNT)는 워치가 연결되어 있고 워치 연동이 켜져 있을 때만 센다(AN16-1).
 */
class WatchLinkRejectedTest {
    private val transport = RecordingTransport()
    private val settings = FakeWatchSettings(SettingsSnapshot.SAFE.copy(isWatchLinked = true))

    @Test
    fun `W43 다시 보내기 요청은 요청 시각을 실은 DataClient 항목으로 남긴다`() = runTest {
        val link = link()
        transport.connected.value = false

        link.requestResend()

        assertEquals(
            listOf(ResendRejectedMessage(T0.toEpochMilli())),
            transport.of("put", WearPaths.RESEND_REJECTED).map {
                ResendRejectedMessage.decode(checkNotNull(it.payload))
            }
        )
        assertEquals(emptyList(), transport.of("send", WearPaths.RESEND_REJECTED), "MessageClient로 보내지 않는다")
    }

    @Test
    fun `W43 아무것도 받지 않았으면 워치 건수는 0이다`() = runTest {
        assertEquals(0, link().observeWatchRejectedCount().first())
    }

    @Test
    fun `W43 워치가 알린 거부 기록 건수를 낸다`() = runTest {
        val link = link()
        val counts = collect(link)

        transport.receive(WearPaths.REJECTED_COUNT, count(2, T0))
        transport.receive(WearPaths.REJECTED_COUNT, count(0, T0.plusSeconds(5)))

        assertEquals(listOf(0, 2, 0), counts)
    }

    @Test
    fun `W43 늦게 도착한 옛 건수는 버린다`() = runTest {
        val link = link()
        val counts = collect(link)

        transport.receive(WearPaths.REJECTED_COUNT, count(1, T0.plusSeconds(5)))
        transport.receive(WearPaths.REJECTED_COUNT, count(4, T0))

        assertEquals(listOf(0, 1), counts)
    }

    @Test
    fun `W43 워치 연결이 끊기면 0으로 보고 다시 연결되면 저장된 건수를 읽는다`() = runTest {
        val link = link()
        val counts = collect(link)
        transport.receive(WearPaths.REJECTED_COUNT, count(2, T0))

        transport.connected.value = false
        transport.storedItems[WearPaths.REJECTED_COUNT] = listOf(count(3, T0.plusSeconds(9)))
        transport.connected.value = true

        assertEquals(listOf(0, 2, 0), counts.take(3))
        assertEquals(3, counts.last())
    }

    @Test
    fun `W43 앱을 띄울 때 이미 저장된 워치 건수를 읽는다`() = runTest {
        transport.storedItems[WearPaths.REJECTED_COUNT] = listOf(count(2, T0))

        assertEquals(2, link().observeWatchRejectedCount().first())
    }

    @Test
    fun `W43 워치 연동을 끄면 워치 건수는 0이다`() = runTest {
        val link = link()
        val counts = collect(link)
        transport.receive(WearPaths.REJECTED_COUNT, count(2, T0))

        settings.state.value = settings.state.value.copy(isWatchLinked = false)

        assertEquals(listOf(0, 2, 0), counts)
    }

    @Test
    fun `W43 해독할 수 없는 건수는 버린다`() = runTest {
        val link = link()
        val counts = collect(link)

        transport.receive(WearPaths.REJECTED_COUNT, "not json".toByteArray())

        assertEquals(listOf(0), counts)
    }

    private fun count(count: Int, at: Instant): ByteArray = RejectedCountMessage(count, at.toEpochMilli()).encode()

    private fun TestScope.collect(link: WatchLink): List<Int> {
        val counts = mutableListOf<Int>()
        scope().launch { link.observeWatchRejectedCount().collect { counts += it } }
        return counts
    }

    private fun TestScope.scope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.link(): WatchLink =
        WatchLink(transport, settings, scope(), clock = Clock.fixed(T0, ZoneOffset.UTC))

    private companion object {
        val T0: Instant = Instant.parse("2026-11-02T01:00:00Z")
    }
}
