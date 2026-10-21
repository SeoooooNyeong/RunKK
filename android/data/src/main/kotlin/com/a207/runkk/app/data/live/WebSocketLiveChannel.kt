package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.BuildConfig
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket 실시간 채널(6.1.14 data.live, 5.7.2 경로 /ws). api.core의 인증된 OkHttp로 열어 접근 토큰이 핸드셰이크
 * Authorization 헤더에 실린다(design-review.md E1).
 *
 * connect()는 핸드셰이크 한 번이고 실패는 Failure(Network)다. 폴링 전환 판단(응답 코드)이 필요한
 * FallbackLiveChannelProvider는 connectOrThrow로 HandshakeFailed(응답 코드)를 받는다. 연결된 뒤 끊기면 1·2·4초 뒤, 이후 5초 간격으로 close()까지 다시 연결한다(D-16, FR-091).
 * 끊긴 동안의 send는 버린다(진행값은 1초마다 새로 보내므로 다음 값이 앞 값을 대신한다).
 */
class WebSocketLiveChannel internal constructor(
    private val client: OkHttpClient,
    private val url: String,
    private val scope: CoroutineScope
) : LiveChannel {
    @Inject
    constructor(client: OkHttpClient, scope: CoroutineScope) : this(client, BuildConfig.LIVE_SOCKET_URL, scope)

    /** 핸드셰이크 실패. code는 업그레이드 대신 받은 HTTP 응답 코드(연결 자체가 안 됐으면 null). */
    class HandshakeFailed(val code: Int?) : IOException("WebSocket 핸드셰이크 실패: ${code ?: "연결 안 됨"}")

    private val decoder = ServerMessageDecoder()

    /** 끊긴 연결을 빨리 알아채도록 핑을 보낸다(대결 진행도 지연 3초 목표, NFR-005). */
    private val socketClient: OkHttpClient by lazy {
        client.newBuilder().pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS).build()
    }
    private val lock = Any()
    private var socket: WebSocket? = null
    private var reconnecting: Job? = null
    private var closed = false

    /** 핸드셰이크는 끝났지만 아직 socket에 넣기 전에 끊긴 소켓. connectOrThrow가 넣는 즉시 다시 연결을 잡는다. lock 안에서만 쓴다. */
    private var lostBeforeAttach: WebSocket? = null

    private val incoming =
        MutableSharedFlow<ServerMessage>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val messages: Flow<ServerMessage> = incoming.asSharedFlow()

    override suspend fun connect(): Outcome<Unit> = try {
        connectOrThrow()
        Outcome.Success(Unit)
    } catch (e: HandshakeFailed) {
        Outcome.Failure(AppError.Network)
    }

    /** 핸드셰이크 한 번. 실패하면 HandshakeFailed(응답 코드). 이미 연결됐거나 닫혔으면 아무것도 하지 않는다. */
    internal suspend fun connectOrThrow() {
        synchronized(lock) { if (closed || socket != null) return }
        val opened = handshake()
        synchronized(lock) {
            when {
                closed -> opened.close(NORMAL_CLOSURE, null)

                lostBeforeAttach === opened -> {
                    lostBeforeAttach = null
                    scheduleReconnectLocked()
                }

                else -> socket = opened
            }
        }
    }

    override fun send(m: ProgressReport) {
        val current = synchronized(lock) { socket } ?: return
        current.send(m.toJsonObject().toString())
    }

    override fun close() {
        val current =
            synchronized(lock) {
                closed = true
                reconnecting?.cancel()
                reconnecting = null
                socket.also { socket = null }
            }
        current?.close(NORMAL_CLOSURE, null)
    }

    private suspend fun handshake(): WebSocket = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).build()
        val webSocket =
            socketClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    private var open = false

                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        open = true
                        if (continuation.isActive) continuation.resume(webSocket)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        decoder.decode(text)?.let(incoming::tryEmit)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(NORMAL_CLOSURE, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        onLost(webSocket)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (!open) {
                            if (continuation.isActive) continuation.resumeWithException(HandshakeFailed(response?.code))
                            return
                        }
                        onLost(webSocket)
                    }
                }
            )
        continuation.invokeOnCancellation { webSocket.cancel() }
    }

    /** 연결된 소켓이 끊겼다. 닫는 중이 아니면 다시 연결한다. 아직 붙이기 전의 소켓이면 붙이는 쪽이 처리하도록 표시한다. */
    private fun onLost(lost: WebSocket) {
        synchronized(lock) {
            if (socket !== lost) {
                lostBeforeAttach = lost
                return
            }
            socket = null
            scheduleReconnectLocked()
        }
    }

    /** 재연결 루프가 없으면 띄운다. 루프가 돌고 있으면 루프가 끝나기 전에 socket을 다시 보므로 띄우지 않는다. lock 안에서만. */
    private fun scheduleReconnectLocked() {
        if (closed || reconnecting != null) return
        reconnecting = scope.launch { reconnect() }
    }

    /** 연결될 때까지 간격을 두고 다시 연결한다. 끝낼지는 lock 안에서 정해 onLost와 엇갈려도 끊긴 채 남지 않는다. */
    private suspend fun reconnect() {
        var attempt = 0
        while (scope.isActive) {
            delay(RECONNECT_DELAYS_MILLIS.getOrElse(attempt) { RECONNECT_STEADY_MILLIS })
            attempt++
            try {
                connectOrThrow()
            } catch (e: IOException) {
                // 다음 간격 뒤 다시 시도한다.
            }
            synchronized(lock) {
                if (closed || socket != null) {
                    reconnecting = null
                    return
                }
            }
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val BUFFER = 64
        const val PING_INTERVAL_SECONDS = 10L

        /** D-16: 1·2·4초 뒤, 이후 5초 간격. */
        val RECONNECT_DELAYS_MILLIS = listOf(1_000L, 2_000L, 4_000L)
        const val RECONNECT_STEADY_MILLIS = 5_000L
    }
}
