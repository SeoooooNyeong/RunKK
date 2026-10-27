package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.SessionRefresher.Refresh
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.LiveState
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * WebSocketLiveChannel 닫기 코드·4401(TC-6466~TC-6468, TC-6472, TC-6473, T-31 앱 쪽, F.3.3, D-110, D-153).
 * 가짜 소켓 공장과 가상 시계로 재연결 간격을 밀리초까지 확인한다. 갱신은 [refreshes] 차례대로 답하는 가짜 refreshNow다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketLiveChannelTest {
    private val sockets = FakeSockets()

    /** refreshNow 응답 차례. 비면 Renewed. */
    private val refreshes = ArrayDeque<Refresh>()
    private val refreshTimes = mutableListOf<Long>()

    private fun TestScope.channel(): WebSocketLiveChannel = WebSocketLiveChannel(
        sockets = sockets,
        url = "ws://test/ws",
        scope = backgroundScope,
        refresh = {
            refreshTimes += testScheduler.currentTime
            refreshes.removeFirstOrNull() ?: Refresh.Renewed("fresh")
        },
        refreshContext = EmptyCoroutineContext,
        now = { testScheduler.currentTime }
    )

    private fun TestScope.statesOf(channel: WebSocketLiveChannel): MutableList<LiveState> {
        val states = mutableListOf<LiveState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { channel.state.collect { states += it } }
        return states
    }

    @Test
    fun `TC-6466 4401이면 갱신 1회 뒤 기다림 없이 곧바로 다시 연결한다`() = runTest {
        val channel = channel()
        channel.connect()
        val states = statesOf(channel)

        sockets.last.closeFromServer(WebSocketLiveChannel.TOKEN_EXPIRED)
        runCurrent()

        assertEquals(listOf(0L), refreshTimes, "갱신 1회")
        assertEquals(2, sockets.attempts, "1초를 기다리지 않고 곧바로 재연결")
        assertEquals(listOf(LiveState.CONNECTED, LiveState.CONNECTING, LiveState.CONNECTED), states)
    }

    @Test
    fun `TC-6467 4401 뒤 갱신이 거부되면 다시 연결하지 않고 닫는다`() = runTest {
        val channel = channel()
        channel.connect()
        refreshes += Refresh.Rejected

        sockets.last.closeFromServer(WebSocketLiveChannel.TOKEN_EXPIRED)
        advanceTimeBy(60_000)

        assertEquals(LiveState.CLOSED, channel.state.value)
        assertEquals(1, sockets.attempts, "재연결 0")
    }

    @Test
    fun `TC-6468 1000·1008은 다시 연결하지 않고 닫는다`() = runTest {
        for (code in listOf(WebSocketLiveChannel.NORMAL_CLOSURE, WebSocketLiveChannel.POLICY_VIOLATION)) {
            val local = FakeSockets()
            val channel =
                WebSocketLiveChannel(local, "ws://test/ws", backgroundScope, now = { testScheduler.currentTime })
            channel.connect()

            local.last.closeFromServer(code)
            advanceTimeBy(60_000)

            assertEquals(LiveState.CLOSED, channel.state.value, "닫기 코드 $code")
            assertEquals(1, local.attempts, "닫기 코드 $code: 재연결 0")
        }
        assertEquals(emptyList(), refreshTimes, "갱신 0")
    }

    @Test
    fun `TC-6468 1012는 1·2·4초 뒤, 이후 5초 간격으로 다시 연결하고 갱신하지 않는다`() = runTest {
        val channel = channel()
        channel.connect()
        sockets.handshake = { 500 }

        sockets.last.closeFromServer(WebSocketLiveChannel.SERVICE_RESTARTED)
        val attemptsAt = mutableListOf<Int>()
        for (at in listOf(999L, 1_001L, 2_999L, 3_001L, 6_999L, 7_001L, 11_999L, 12_001L, 17_001L)) {
            advanceTimeBy(at - testScheduler.currentTime)
            attemptsAt += sockets.attempts
        }

        assertEquals(listOf(1, 2, 2, 3, 3, 4, 4, 5, 6), attemptsAt)
        assertEquals(emptyList(), refreshTimes)
        assertEquals(LiveState.CONNECTING, channel.state.value)
    }

    @Test
    fun `TC-6472 4401 뒤 갱신 일시 오류면 닫은 채 1·2·4·5초 간격으로 갱신과 재연결을 다시 한다`() = runTest {
        val channel = channel()
        channel.connect()
        repeat(4) { refreshes += Refresh.Unavailable }

        sockets.last.closeFromServer(WebSocketLiveChannel.TOKEN_EXPIRED)
        advanceTimeBy(11_999)
        assertEquals(1, sockets.attempts, "갱신 전에는 다시 연결하지 않는다")
        advanceTimeBy(2)

        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L, 12_000L), refreshTimes)
        assertEquals(2, sockets.attempts, "갱신이 되자 재연결 1회")
        assertEquals(LiveState.CONNECTED, channel.state.value)
    }

    @Test
    fun `TC-6473 (a) 1분 안에 세 번째 4401은 곧바로 재연결하지 않고 일반 간격으로 넘긴다`() = runTest {
        val channel = channel()
        channel.connect()

        expire()
        assertEquals(2, sockets.attempts, "첫째: 곧바로")
        advanceTimeBy(5_000)
        expire()
        assertEquals(3, sockets.attempts, "둘째: 곧바로")
        advanceTimeBy(5_000)
        expire()
        assertEquals(3, sockets.attempts, "셋째: 곧바로 하지 않는다")
        advanceTimeBy(999)
        assertEquals(3, sockets.attempts)
        advanceTimeBy(2)
        assertEquals(4, sockets.attempts, "일반 간격 첫 대기 1초 뒤")
        assertEquals(LiveState.CONNECTED, channel.state.value)
    }

    @Test
    fun `TC-6473 (b) 연결이 1분 넘게 이어지면 반복 횟수를 0부터 다시 센다`() = runTest {
        val channel = channel()
        channel.connect()
        expire()
        advanceTimeBy(5_000)
        expire()

        advanceTimeBy(61_000)
        expire()

        assertEquals(4, sockets.attempts, "61초 유지 뒤 4401은 첫째로 세어 곧바로 재연결")
        channel.close()
    }

    @Test
    fun `TC-6473 (c) 60초 정각은 1분 넘게가 아니라 세 번째로 센다`() = runTest {
        val channel = channel()
        channel.connect()
        expire()
        advanceTimeBy(5_000)
        expire()

        advanceTimeBy(60_000)
        expire()

        assertEquals(3, sockets.attempts, "60초 정각: 셋째라 일반 간격")
        advanceTimeBy(1_001)
        assertEquals(4, sockets.attempts)
    }

    @Test
    fun `닫은 채널은 서버가 닫아도 다시 연결하지 않는다`() = runTest {
        val channel = channel()
        channel.connect()
        val socket = sockets.last

        channel.close()
        socket.closeFromServer(WebSocketLiveChannel.SERVICE_RESTARTED)
        advanceTimeBy(30_000)

        assertEquals(1, sockets.attempts)
        assertEquals(LiveState.CLOSED, channel.state.value)
    }

    @Test
    fun `연결이 끊기면 같은 간격으로 다시 연결하고 받은 메시지를 내보낸다`() = runTest {
        val channel = channel()
        channel.connect()
        val received = mutableListOf<Any>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { channel.messages.collect { received += it } }

        sockets.last.drop()
        advanceTimeBy(1_001)
        sockets.last.receive("""{"type":"CrewRunEnded","postId":9}""")

        assertEquals(2, sockets.attempts)
        assertEquals(1, received.size)
    }

    @Test
    fun `AN5-C-4 재연결 핸드셰이크가 401이면 다음 시도에서 갱신부터 하고 거부되면 닫는다`() = runTest {
        val channel = channel()
        channel.connect()
        sockets.handshake = { WebSocketLiveChannel.UNAUTHORIZED }
        refreshes += Refresh.Rejected

        sockets.last.closeFromServer(WebSocketLiveChannel.SERVICE_RESTARTED)
        advanceTimeBy(60_000)

        assertEquals(listOf(3_000L), refreshTimes, "401 다음 간격(1초 뒤 실패, 2초 대기)에서 갱신 1회")
        assertEquals(2, sockets.attempts, "갱신 거부 뒤 재연결 0")
        assertEquals(LiveState.CLOSED, channel.state.value)
    }

    @Test
    fun `AN5-C-4 4401 갱신 뒤 곧바로 재연결이 401이면 다음 시도에서 다시 갱신하고 거부되면 닫는다`() = runTest {
        val channel = channel()
        channel.connect()
        sockets.handshake = { WebSocketLiveChannel.UNAUTHORIZED }
        refreshes += Refresh.Renewed("fresh")
        refreshes += Refresh.Rejected

        sockets.last.closeFromServer(WebSocketLiveChannel.TOKEN_EXPIRED)
        advanceTimeBy(60_000)

        assertEquals(listOf(0L, 1_000L), refreshTimes)
        assertEquals(2, sockets.attempts)
        assertEquals(LiveState.CLOSED, channel.state.value)
    }

    @Test
    fun `AN5-C-4 재연결 핸드셰이크 401 뒤 갱신이 일시 오류면 간격을 두고 갱신과 재연결을 이어 간다`() = runTest {
        val channel = channel()
        channel.connect()
        sockets.handshake = { WebSocketLiveChannel.UNAUTHORIZED }
        refreshes += Refresh.Unavailable

        sockets.last.closeFromServer(WebSocketLiveChannel.SERVICE_RESTARTED)
        advanceTimeBy(3_001)
        sockets.handshake = { null }
        advanceTimeBy(4_000)

        assertEquals(listOf(3_000L, 7_000L), refreshTimes, "일시 오류 뒤 다음 간격에서 다시 갱신")
        assertEquals(3, sockets.attempts)
        assertEquals(LiveState.CONNECTED, channel.state.value)
    }

    @Test
    fun `AN6-3 4401 갱신은 되는데 재연결 핸드셰이크가 계속 401이면 세 번째에 닫고 Unauthorized로 알린다`() = runTest {
        val channel = channel()
        channel.connect()
        sockets.handshake = { WebSocketLiveChannel.UNAUTHORIZED }

        sockets.last.closeFromServer(WebSocketLiveChannel.TOKEN_EXPIRED)
        advanceTimeBy(60_000)

        assertEquals(listOf(0L, 1_000L, 3_000L), refreshTimes, "갱신 Renewed 뒤 401이 세 번이면 더 갱신하지 않는다")
        assertEquals(4, sockets.attempts, "처음 연결 + 재연결 401 세 번")
        assertEquals(LiveState.CLOSED, channel.state.value)
        assertEquals(Outcome.Failure(AppError.Unauthorized), channel.connect())
        assertEquals(4, sockets.attempts, "닫힌 채널의 connect는 핸드셰이크하지 않는다")
    }

    @Test
    fun `AN6-3 끊김 뒤 재연결에서 갱신 Renewed와 핸드셰이크 401이 세 번 이어지면 닫는다`() = runTest {
        val channel = channel()
        channel.connect()
        sockets.handshake = { WebSocketLiveChannel.UNAUTHORIZED }

        sockets.last.closeFromServer(WebSocketLiveChannel.SERVICE_RESTARTED)
        advanceTimeBy(60_000)

        // 1초 뒤 첫 시도는 갱신 없이 401(세지 않음), 그 뒤 3·7·12초에 갱신하고 401
        assertEquals(listOf(3_000L, 7_000L, 12_000L), refreshTimes)
        assertEquals(5, sockets.attempts)
        assertEquals(LiveState.CLOSED, channel.state.value)
    }

    @Test
    fun `AN6-3 연결에 성공하면 갱신 뒤 401 횟수를 0부터 다시 센다`() = runTest {
        val channel = channel()
        channel.connect()
        sockets.handshake = { WebSocketLiveChannel.UNAUTHORIZED }
        expire()
        advanceTimeBy(1_001)
        sockets.handshake = { null }
        advanceTimeBy(2_000)
        assertEquals(LiveState.CONNECTED, channel.state.value, "401 두 번 뒤 세 번째 시도에서 연결")

        sockets.handshake = { WebSocketLiveChannel.UNAUTHORIZED }
        expire()
        advanceTimeBy(1_001)
        assertEquals(LiveState.CONNECTING, channel.state.value, "연결 뒤 401 두 번은 아직 상한 아래")
        advanceTimeBy(2_000)

        assertEquals(listOf(0L, 1_000L, 3_000L, 3_001L, 4_001L, 6_001L), refreshTimes)
        assertEquals(LiveState.CLOSED, channel.state.value, "연결 뒤 세 번째 401에서 닫는다")
    }

    @Test
    fun `AN6-3 1008이나 갱신 거부로 닫힌 채널의 connect는 Unauthorized다`() = runTest {
        val local = FakeSockets()
        val policy = WebSocketLiveChannel(local, "ws://test/ws", backgroundScope, now = { testScheduler.currentTime })
        policy.connect()
        local.last.closeFromServer(WebSocketLiveChannel.POLICY_VIOLATION)
        runCurrent()
        assertEquals(Outcome.Failure(AppError.Unauthorized), policy.connect())

        val rejected = channel()
        rejected.connect()
        refreshes += Refresh.Rejected
        expire()
        assertEquals(Outcome.Failure(AppError.Unauthorized), rejected.connect())
        assertEquals(1, local.attempts, "1008로 닫힌 뒤 다시 핸드셰이크하지 않는다")
        assertEquals(1, sockets.attempts, "갱신 거부로 닫힌 뒤 다시 핸드셰이크하지 않는다")
    }

    /** 지금 소켓을 서버가 4401로 닫고 곧바로 처리되게 한다. */
    private fun TestScope.expire() {
        sockets.last.closeFromServer(WebSocketLiveChannel.TOKEN_EXPIRED)
        runCurrent()
    }
}
