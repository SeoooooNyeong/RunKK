package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.ApiCoreBindings
import com.a207.runkk.app.api.live.LiveApi
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/**
 * FallbackLiveChannelProvider(14장 단위(앱 데이터), D-57): 핸드셰이크 404·426·3회 실패 → 폴링, 성공이면 WebSocket.
 * 서버는 MockWebServer(/ws 업그레이드, /api/v1/live/poll·progress)다. 실제 소켓을 쓰므로 실시간으로 돈다.
 */
class FallbackLiveChannelProviderTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketAttempts = AtomicInteger()
    private val polls = AtomicInteger()
    private val progressBodies = LinkedBlockingQueue<String>()
    private val socketMessages = LinkedBlockingQueue<String>()

    /** /ws 요청에 줄 응답. 기본은 업그레이드 성공. */
    private var socketReply: () -> MockResponse = ::upgrade

    private val provider: FallbackLiveChannelProvider by lazy {
        server.dispatcher = routes()
        server.start()
        val client = OkHttpClient()
        val json = ApiCoreBindings.json()
        val liveApi: LiveApi =
            Retrofit
                .Builder()
                .baseUrl(server.url("/api/v1/"))
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create()
        val socketUrl = server.url("/ws").toString()
        FallbackLiveChannelProvider(
            webSockets = Provider { WebSocketLiveChannel(client, socketUrl, scope) },
            pollers = Provider { PollingLiveChannel(liveApi, scope) },
            scope = scope
        )
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    @Test
    fun `핸드셰이크가 404면 바로 폴링으로 바꾼다`() = assertFallsBackToPolling(status = 404, expectedAttempts = 1)

    @Test
    fun `핸드셰이크가 426이면 바로 폴링으로 바꾼다`() = assertFallsBackToPolling(status = 426, expectedAttempts = 1)

    @Test
    fun `핸드셰이크가 3번 이어서 실패하면 폴링으로 바꾼다`() = assertFallsBackToPolling(status = 500, expectedAttempts = 3)

    @Test
    fun `핸드셰이크가 되면 WebSocket으로 주고받는다`() = runBlocking {
        val channel = provider.open()
        val first = async(start = CoroutineStart.UNDISPATCHED) { channel.messages.first() }

        withTimeout(TIMEOUT_MILLIS) { channel.connect() }
        channel.send(REPORT)

        assertEquals(CrewRunEnded(9), withTimeout(TIMEOUT_MILLIS) { first.await() })
        assertEquals(REPORT.toJsonObject().toString(), socketMessages.poll(5, TimeUnit.SECONDS))
        assertEquals(1, socketAttempts.get())
        assertEquals(0, polls.get(), "WebSocket이 되면 폴링하지 않는다")
        channel.close()
    }

    @Test
    fun `닫기 전에는 같은 채널을 주고 connect를 여러 번 불러도 한 번만 연결한다`() = runBlocking {
        val channel = provider.open()
        assertSame(channel, provider.open())

        withTimeout(TIMEOUT_MILLIS) {
            channel.connect()
            channel.connect()
        }
        assertSame(channel, provider.open())
        assertEquals(1, socketAttempts.get())

        channel.close()
        assertNotSame(channel, provider.open())
    }

    private fun assertFallsBackToPolling(status: Int, expectedAttempts: Int) = runBlocking {
        socketReply = { MockResponse.Builder().code(status).build() }
        val channel = provider.open()
        val first = async(start = CoroutineStart.UNDISPATCHED) { channel.messages.first() }

        withTimeout(TIMEOUT_MILLIS) { channel.connect() }
        channel.send(REPORT)

        val received: ServerMessage = withTimeout(TIMEOUT_MILLIS) { first.await() }
        assertEquals(CrewRunEnded(9), received, "폴링으로 받은 메시지")
        assertEquals(REPORT.toJsonObject().toString(), progressBodies.poll(5, TimeUnit.SECONDS), "폴링으로 보낸 진행값")
        assertEquals(expectedAttempts, socketAttempts.get())
        channel.close()
    }

    private fun routes() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
            "/ws" -> {
                socketAttempts.incrementAndGet()
                socketReply()
            }

            "/api/v1/live/poll" -> {
                val body = if (polls.getAndIncrement() == 0) """[{"type":"CrewRunEnded","postId":9}]""" else "[]"
                MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()
            }

            "/api/v1/live/progress" -> {
                progressBodies.add(request.body?.utf8().orEmpty())
                MockResponse.Builder().code(200).build()
            }

            else -> MockResponse.Builder().code(404).build()
        }
    }

    private fun upgrade(): MockResponse = MockResponse
        .Builder()
        .webSocketUpgrade(
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"CrewRunEnded","postId":9}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    socketMessages.add(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }
            }
        ).build()

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        val REPORT =
            ProgressReport(ProgressReport.Channel.CREW_RUN, 9, 1_200, 400, Instant.parse("2026-10-20T21:06:40Z"))
    }
}
