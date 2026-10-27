package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.SessionRefresher.Refresh
import com.a207.runkk.app.api.live.LiveApi
import com.a207.runkk.app.domain.live.ChatPresence
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * FallbackLiveChannelProvider 연결 임대(TC-6461~TC-6465, TC-6471, D-103, D-131). 가짜 소켓 공장과 가상 시계를 쓴다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveChannelLeaseTest {
    private val sockets = FakeSockets()
    private val polled = mutableListOf<JsonObject>()

    private fun TestScope.provider(): FallbackLiveChannelProvider = FallbackLiveChannelProvider(
        webSockets =
            Provider {
                WebSocketLiveChannel(
                    sockets,
                    "ws://test/ws",
                    backgroundScope,
                    refresh = { Refresh.Renewed("fresh") },
                    refreshContext = EmptyCoroutineContext
                )
            },
        pollers = Provider { PollingLiveChannel(FakeLiveApi(), backgroundScope) },
        scope = backgroundScope
    )

    private fun TestScope.collect(
        channel: com.a207.runkk.app.domain.live.LiveChannel
    ): Pair<MutableList<ServerMessage>, Job> {
        val received = mutableListOf<ServerMessage>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            channel.messages.collect {
                received +=
                    it
            }
        }
        return received to job
    }

    @Test
    fun `TC-6461 임대 둘이 함께 연결해도 핸드셰이크는 한 번이고 서버 메시지를 둘 다 받는다`() = runTest {
        val provider = provider()
        val battle = provider.open()
        val chat = provider.open()
        val (a, _) = collect(battle)
        val (b, _) = collect(chat)

        battle.connect()
        chat.connect()
        sockets.last.receive(ENDED)
        runCurrent()

        assertEquals(1, sockets.attempts)
        assertEquals(listOf<ServerMessage>(CrewRunEnded(9)), a)
        assertEquals(listOf<ServerMessage>(CrewRunEnded(9)), b)
    }

    @Test
    fun `TC-6462 한 임대를 반납해도 연결은 남고 다른 임대는 계속 받으며 반납한 임대의 받기는 끝난다`() = runTest {
        val provider = provider()
        val battle = provider.open()
        val chat = provider.open()
        val (a, aJob) = collect(battle)
        val (b, _) = collect(chat)
        battle.connect()

        battle.close()
        runCurrent()
        sockets.last.receive(ENDED)
        runCurrent()

        assertEquals(null, sockets.last.closedByApp, "서버로 닫기 0")
        assertEquals(listOf<ServerMessage>(CrewRunEnded(9)), b)
        assertEquals(emptyList(), a)
        assertTrue(aJob.isCompleted, "반납한 임대의 messages는 끝난다")
    }

    @Test
    fun `TC-6463 마지막 임대를 반납하면 연결을 닫고 다음 연결은 새 핸드셰이크다`() = runTest {
        val provider = provider()
        val battle = provider.open()
        val chat = provider.open()
        battle.connect()
        val first = sockets.last

        battle.close()
        chat.close()

        assertEquals(WebSocketLiveChannel.NORMAL_CLOSURE, first.closedByApp, "참조 수 0에서 실제 닫기 1회")
        assertEquals(LiveState.CLOSED, chat.state.value)
        provider.open().connect()
        assertEquals(2, sockets.attempts, "새 핸드셰이크 1회")
    }

    @Test
    fun `TC-6464 같은 임대를 두 번 반납해도 다른 임대를 끊지 않는다`() = runTest {
        val provider = provider()
        val battle = provider.open()
        val chat = provider.open()
        val (b, _) = collect(chat)
        battle.connect()

        battle.close()
        battle.close()
        sockets.last.receive(ENDED)
        runCurrent()

        assertEquals(null, sockets.last.closedByApp)
        assertEquals(LiveState.CONNECTED, chat.state.value)
        assertEquals(listOf<ServerMessage>(CrewRunEnded(9)), b)
    }

    @Test
    fun `TC-6465 진행값과 채팅방 입장을 type과 함께 보내고 위치 항목이 없다`() = runTest {
        val provider = provider()
        val battle = provider.open()
        val chat = provider.open()
        battle.connect()

        battle.send(ProgressReport(ProgressReport.Channel.BATTLE, 7, 35, 11, Instant.parse("2026-10-22T10:00:11Z")))
        chat.send(ChatPresence(5, isPresent = true))

        val (progress, presence) = sockets.last.sent.map { Json.parseToJsonElement(it).jsonObject }
        assertEquals("Progress", progress["type"]!!.jsonPrimitive.content)
        assertEquals("BATTLE", progress["channel"]!!.jsonPrimitive.content)
        assertEquals("7", progress["channelId"]!!.jsonPrimitive.content)
        assertEquals(
            "2026-10-22T19:00:11+09:00",
            progress["sentAt"]!!.jsonPrimitive.content,
            "서버 readInbound는 오프셋 ISO도 받는다"
        )
        assertEquals(Json.parseToJsonElement("""{"type":"ChatPresence","crewId":5,"present":true}"""), presence)
        val keys = progress.keys + presence.keys
        assertTrue("isPresent" !in keys && keys.none { it in setOf("latitude", "longitude", "course") }, "$keys")
    }

    @Test
    fun `TC-6471 핸드셰이크가 404면 임대 state가 POLLING이고 채팅방 입장은 폴링으로 보내지 않는다`() = runTest {
        sockets.handshake = { 404 }
        val provider = provider()
        val chat = provider.open()

        chat.connect()
        chat.send(ChatPresence(5, isPresent = true))
        chat.send(ProgressReport(ProgressReport.Channel.CREW_RUN, 3, 10, 2, Instant.parse("2026-10-22T10:00:11Z")))
        advanceTimeBy(10)

        assertEquals(LiveState.POLLING, chat.state.value)
        assertEquals(listOf("Progress"), polled.map { it["type"]!!.jsonPrimitive.content })
        chat.close()
    }

    @Test
    fun `T-31 4401 재연결 뒤에도 두 임대가 같은 연결로 계속 받는다`() = runTest {
        val provider = provider()
        val battle = provider.open()
        val chat = provider.open()
        val (a, _) = collect(battle)
        val (b, _) = collect(chat)
        battle.connect()

        sockets.last.closeFromServer(WebSocketLiveChannel.TOKEN_EXPIRED)
        runCurrent()
        sockets.last.receive(ENDED)
        runCurrent()

        assertEquals(2, sockets.attempts, "재연결은 임대마다가 아니라 한 번")
        assertEquals(LiveState.CONNECTED, chat.state.value)
        assertEquals(1, a.size)
        assertEquals(1, b.size)
    }

    @Test
    fun `연결이 1000으로 닫히면 임대 state가 CLOSED이고 다음 open은 새 연결이다`() = runTest {
        val provider = provider()
        val chat = provider.open()
        chat.connect()

        sockets.last.closeFromServer(WebSocketLiveChannel.NORMAL_CLOSURE)
        runCurrent()

        assertEquals(LiveState.CLOSED, chat.state.value)
        provider.open().connect()
        assertEquals(2, sockets.attempts)
    }

    @Test
    fun `AN5-C-1 마지막 임대 반납과 새 임대가 다른 스레드에서 겹쳐도 끝난 연결을 임대하지 않는다`() {
        val scope = CoroutineScope(SupervisorJob())
        val provider =
            FallbackLiveChannelProvider(
                webSockets = Provider { error("이 시험은 연결하지 않는다") },
                pollers = Provider { error("이 시험은 연결하지 않는다") },
                scope = scope
            )
        val threads = Executors.newFixedThreadPool(2)
        val barrier = CyclicBarrier(2)
        var endedLeases = 0
        try {
            repeat(RACE_ROUNDS) {
                val held = provider.open()
                val releasing = threads.submit {
                    barrier.await()
                    held.close()
                }
                val opening = threads.submit<com.a207.runkk.app.domain.live.LiveChannel> {
                    barrier.await()
                    provider.open()
                }
                releasing.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                val lease = opening.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (lease.state.value == LiveState.CLOSED) endedLeases++
                lease.close()
            }
        } finally {
            threads.shutdownNow()
            scope.cancel()
        }

        assertEquals(0, endedLeases, "끝난 연결(CLOSED)의 임대를 돌려준 횟수")
    }

    @Test
    fun `AN5-C-1 반납으로 끝난 연결 뒤 open은 끝나지 않은 새 연결의 임대다`() {
        val scope = CoroutineScope(SupervisorJob())
        val provider =
            FallbackLiveChannelProvider(
                webSockets = Provider { error("이 시험은 연결하지 않는다") },
                pollers = Provider { error("이 시험은 연결하지 않는다") },
                scope = scope
            )

        provider.open().close()
        val next = provider.open()

        assertNotEquals(LiveState.CLOSED, next.state.value)
        next.close()
        scope.cancel()
    }

    private inner class FakeLiveApi : LiveApi {
        override suspend fun poll(): JsonArray = JsonArray(emptyList())

        override suspend fun progress(body: JsonObject) {
            polled += body
        }
    }

    private companion object {
        const val RACE_ROUNDS = 50_000
        const val RACE_TIMEOUT_SECONDS = 5L
        const val ENDED = """{"type":"CrewRunEnded","postId":9}"""
    }
}
