package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.BuildConfig
import com.a207.runkk.app.api.core.SessionRefresher
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.ClientMessage
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.ServerMessage
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket 실시간 채널(6.1.14 data.live, 5.7.2 경로 /ws). api.core의 인증된 OkHttp로 열어 접근 토큰이 핸드셰이크
 * Authorization 헤더에 실린다(design-review.md E1).
 *
 * 핸드셰이크가 401이면 OkHttp가 TokenAuthenticator로 접근 토큰을 갱신해 같은 핸드셰이크를 한 번 다시 보낸다. connect()는 핸드셰이크
 * 한 번이고 실패는 Failure(Network), 갱신 뒤에도 401이면 Failure(Unauthorized)다. 폴링 전환 판단(응답 코드)이 필요한
 * FallbackLiveChannelProvider는 connectOrThrow로 HandshakeFailed(응답 코드)를 받는다.
 *
 * 연결된 뒤 서버가 닫으면 닫기 코드로 갈린다(F.3.3, D-110, D-153):
 * - 1000 NORMAL(같은 회원의 새 연결이 이 연결을 대신함): 재연결하지 않고 닫는다(CLOSED).
 * - 1008 POLICY_VIOLATION(인증 실패): 재연결하지 않고 닫는다(Unauthorized, CLOSED).
 * - 4401 TOKEN_EXPIRED: SessionRefresher.refreshNow()로 갱신하고 곧바로 다시 연결한다(재연결 간격·실패 횟수에 넣지 않는다).
 *   갱신이 거부되면 로그인 상태가 이미 거짓이므로 닫는다(CLOSED). 갱신 일시 오류는 닫은 채 일반 간격으로 "갱신 → 재연결"을 다시 한다.
 *   곧바로 재연결이 1분 안에 3번 반복되면(연결이 1분 넘게 이어지면 다시 0부터 센다) 일반 간격으로 넘긴다.
 * - 그 밖(1012 SERVICE_RESTARTED, 연결 끊김): 1·2·4초 뒤, 이후 5초 간격으로 close()까지 다시 연결한다(D-16, FR-091).
 * 재연결 핸드셰이크가 401이면 다음 시도에서 갱신부터 한다. 갱신은 되는데(Renewed) 곧 이은 핸드셰이크가 401인 일이 연결 없이 3번
 * 이어지면(서버가 이 소켓만 거부, 계정 정지 등) 끝없이 갱신·재연결하지 않고 닫는다(AN6-3, D-153의 "3번" 상한과 같은 정신).
 * 인증 때문에 닫힌 채널(1008, 갱신 거부, 이 상한)은 state가 CLOSED이고 그 뒤 connect()는 핸드셰이크 없이 Failure(Unauthorized)다.
 * 끊긴 동안의 send는 버린다(진행값은 1초마다 새로 보내고, 채팅방 입장은 ChatRoom이 CONNECTED 진입 때 다시 보낸다, D-131).
 */
class WebSocketLiveChannel internal constructor(
    private val sockets: WebSocket.Factory,
    private val url: String,
    private val scope: CoroutineScope,
    private val decoder: ServerMessageDecoder = ServerMessageDecoder(),
    /** SessionRefresher.refreshNow(블로킹). */
    private val refresh: () -> SessionRefresher.Refresh = { SessionRefresher.Refresh.Unavailable },
    /** 갱신(블로킹 HTTP)을 돌릴 문맥. 시험은 시험 디스패처를 넘긴다. */
    private val refreshContext: CoroutineContext = Dispatchers.IO,
    /** 지금 시각(밀리초). 4401 반복 횟수를 1분 단위로 센다. */
    private val now: () -> Long = System::currentTimeMillis
) : LiveChannel {
    @Inject
    constructor(
        client: OkHttpClient,
        scope: CoroutineScope,
        decoder: ServerMessageDecoder,
        refresher: SessionRefresher
    ) :
        this(pinging(client), BuildConfig.LIVE_SOCKET_URL, scope, decoder, refresher::refreshNow)

    internal constructor(client: OkHttpClient, url: String, scope: CoroutineScope) :
        this(sockets = pinging(client), url = url, scope = scope)

    /** 핸드셰이크 실패. code는 업그레이드 대신 받은 HTTP 응답 코드(연결 자체가 안 됐으면 null). */
    class HandshakeFailed(val code: Int?) : IOException("WebSocket 핸드셰이크 실패: ${code ?: "연결 안 됨"}")

    private val lock = Any()
    private var socket: WebSocket? = null
    private var reconnecting: Job? = null
    private var closed = false

    /** 인증 때문에 닫혔으면 그 오류(Unauthorized). connect()가 돌려준다. lock 안에서만 쓴다. */
    private var closedBy: AppError? = null

    /** 갱신(Renewed) 직후 핸드셰이크가 401인 재연결이 연결 없이 이어진 횟수(AN6-3). 연결되면 0. lock 안에서만 쓴다. */
    private var unauthorizedAfterRenewal = 0

    /** 핸드셰이크는 끝났지만 아직 socket에 넣기 전에 끊긴 소켓과 그 닫기 코드. connectOrThrow가 넣는 즉시 처리한다. lock 안에서만. */
    private var lostBeforeAttach: Pair<WebSocket, Int?>? = null

    /** 4401 → 갱신 → 곧바로 재연결의 반복 횟수와 마지막으로 연결된 시각(D-153 (2)). lock 안에서만 쓴다. */
    private var rapidReconnects = 0
    private var connectedAtMillis: Long? = null

    private val incoming =
        MutableSharedFlow<ServerMessage>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val messages: Flow<ServerMessage> = incoming.asSharedFlow()

    private val connection = MutableStateFlow(LiveState.CONNECTING)
    override val state: StateFlow<LiveState> = connection.asStateFlow()

    override suspend fun connect(): Outcome<Unit> {
        synchronized(lock) { closedBy }?.let { return Outcome.Failure(it) }
        return try {
            connectOrThrow()
            Outcome.Success(Unit)
        } catch (e: HandshakeFailed) {
            Outcome.Failure(if (e.code == UNAUTHORIZED) AppError.Unauthorized else AppError.Network)
        }
    }

    /** 핸드셰이크 한 번. 실패하면 HandshakeFailed(응답 코드). 이미 연결됐거나 닫혔으면 아무것도 하지 않는다. */
    internal suspend fun connectOrThrow() {
        synchronized(lock) { if (closed || socket != null) return }
        val opened = handshake()
        val lostCode: Int?
        synchronized(lock) {
            val lost = lostBeforeAttach?.takeIf { it.first === opened }
            when {
                closed -> {
                    opened.close(NORMAL_CLOSURE, null)
                    return
                }

                lost != null -> {
                    lostBeforeAttach = null
                    lostCode = lost.second
                }

                else -> {
                    socket = opened
                    connectedAtMillis = now()
                    unauthorizedAfterRenewal = 0
                    connection.value = LiveState.CONNECTED
                    return
                }
            }
        }
        onLostAttached(lostCode)
    }

    override fun send(m: ClientMessage) {
        val current = synchronized(lock) { socket } ?: return
        current.send(m.toJsonObject().toString())
    }

    override fun close() {
        val current =
            synchronized(lock) {
                closed = true
                connection.value = LiveState.CLOSED
                reconnecting?.cancel()
                reconnecting = null
                socket.also { socket = null }
            }
        current?.close(NORMAL_CLOSURE, null)
    }

    private suspend fun handshake(): WebSocket = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).build()
        val webSocket =
            sockets.newWebSocket(
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
                        onLost(webSocket, code)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        onLost(webSocket, code)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (!open) {
                            if (continuation.isActive) continuation.resumeWithException(HandshakeFailed(response?.code))
                            return
                        }
                        onLost(webSocket, null)
                    }
                }
            )
        continuation.invokeOnCancellation { webSocket.cancel() }
    }

    /**
     * 소켓이 끊겼다(code는 서버 닫기 코드, 연결 끊김이면 null). 한 소켓에 onClosing·onClosed가 함께 와도 붙어 있는 소켓만 처리하므로
     * 한 번이다. 아직 붙이기 전의 소켓이면 붙이는 쪽이 처리하도록 표시한다(이미 처리한 소켓의 늦은 알림은 붙일 때 소켓이 달라 무시된다).
     */
    private fun onLost(lost: WebSocket, code: Int?) {
        synchronized(lock) {
            if (closed) return
            if (socket !== lost) {
                lostBeforeAttach = lost to code
                return
            }
            socket = null
        }
        onLostAttached(code)
    }

    /** 붙어 있던 연결을 잃은 뒤의 처리. lock 밖에서 부른다. */
    private fun onLostAttached(code: Int?) {
        when (code) {
            NORMAL_CLOSURE -> closeForGood()

            POLICY_VIOLATION -> closeForGood(AppError.Unauthorized)

            TOKEN_EXPIRED -> synchronized(lock) {
                if (closed || reconnecting != null) return
                connection.value = LiveState.CONNECTING
                reconnecting = scope.launch { releasingOnExit { renewAndReconnect() } }
            }

            else -> synchronized(lock) {
                if (!closed) connection.value = LiveState.CONNECTING
                scheduleReconnectLocked(refreshFirst = false)
            }
        }
    }

    /**
     * 다시 연결하지 않고 닫는다(1000: 새 연결이 이미 있다, [reason] Unauthorized: 1008·갱신 거부·갱신 뒤 401 반복). 감싼 쪽은
     * state CLOSED로 알고, 인증 때문에 닫혔으면 그 뒤 connect()가 Failure([reason])를 받는다.
     */
    private fun closeForGood(reason: AppError? = null) {
        synchronized(lock) {
            if (closed) return
            closed = true
            closedBy = reason
            reconnecting?.cancel()
            reconnecting = null
            connection.value = LiveState.CLOSED
        }
    }

    /** 4401: 갱신하고 곧바로 다시 연결한다. 일시 오류·반복 상한·연결 실패면 일반 간격 재연결로 넘긴다. */
    private suspend fun renewAndReconnect() {
        val renewal = withContext(refreshContext) { refresh() }
        if (renewal == SessionRefresher.Refresh.Rejected) {
            closeForGood(AppError.Unauthorized)
            return
        }
        val isImmediate = renewal is SessionRefresher.Refresh.Renewed && countRapidReconnect()
        val needsRefresh =
            renewal == SessionRefresher.Refresh.Unavailable || (isImmediate && tryConnect(afterRenewal = true))
        reconnectLoop(refreshFirst = needsRefresh)
    }

    /** 곧바로 재연결 횟수를 센다. 연결이 1분 넘게 이어졌으면 0부터 다시 센다. 상한 안이면 참. */
    private fun countRapidReconnect(): Boolean = synchronized(lock) {
        val since = connectedAtMillis
        if (since != null && now() - since > RAPID_WINDOW_MILLIS) rapidReconnects = 0
        rapidReconnects++
        rapidReconnects < RAPID_LIMIT
    }

    /** 재연결 루프가 없으면 띄운다. 루프가 돌고 있으면 루프가 끝나기 전에 socket을 다시 보므로 띄우지 않는다. lock 안에서만. */
    private fun scheduleReconnectLocked(refreshFirst: Boolean) {
        if (closed || reconnecting != null) return
        reconnecting = scope.launch { releasingOnExit { reconnectLoop(refreshFirst) } }
    }

    /** 재연결 코루틴이 어떻게 끝나든(예상 밖 예외 포함) 자기 Job이면 reconnecting을 비워 다음 끊김에 다시 띄울 수 있게 한다. */
    private suspend fun releasingOnExit(block: suspend () -> Unit) {
        val self = currentCoroutineContext()[Job]
        try {
            block()
        } finally {
            synchronized(lock) { if (reconnecting === self) reconnecting = null }
        }
    }

    /**
     * 연결될 때까지 간격(1·2·4초, 이후 5초)을 두고 다시 연결한다. [refreshFirst]면 다음 시도에서 먼저 갱신한다(4401 뒤 갱신 일시 오류,
     * 핸드셰이크 401). 갱신이 거부되면 닫고, 일시 오류면 다음 간격을 기다린다. 끝낼지는 lock 안에서 정해 onLost와 엇갈려도 끊긴 채 남지 않는다.
     */
    private suspend fun reconnectLoop(refreshFirst: Boolean) {
        var needsRefresh = refreshFirst
        var attempt = 0
        while (scope.isActive && !isSettled()) {
            delay(RECONNECT_DELAYS_MILLIS.getOrElse(attempt) { RECONNECT_STEADY_MILLIS })
            attempt++
            needsRefresh = reconnectOnce(needsRefresh)
        }
    }

    /** 닫혔거나 다시 붙었으면 참이고 재연결 Job을 비운다. lock 안에서 정한다. */
    private fun isSettled(): Boolean = synchronized(lock) {
        (closed || socket != null).also { settled -> if (settled) reconnecting = null }
    }

    /** 재연결 한 번. [needsRefresh]면 먼저 갱신한다(거부면 닫는다). 돌려준 값은 다음 시도에서 갱신부터 할지다. */
    private suspend fun reconnectOnce(needsRefresh: Boolean): Boolean {
        val renewal = if (needsRefresh) withContext(refreshContext) { refresh() } else null
        return when (renewal) {
            SessionRefresher.Refresh.Rejected -> {
                closeForGood(AppError.Unauthorized)
                false
            }

            SessionRefresher.Refresh.Unavailable -> true

            else -> tryConnect(afterRenewal = renewal is SessionRefresher.Refresh.Renewed)
        }
    }

    /**
     * 핸드셰이크 한 번. 실패는 다음 간격에 다시 한다. 401이면 참: OkHttp의 TokenAuthenticator 갱신까지 실패한 것이라(갱신 토큰 거부로
     * 로그아웃됐을 수 있다) 다음 시도에서 refreshNow로 먼저 확인한다. 거부면 닫아 로그아웃 뒤 끝없이 다시 연결하지 않는다.
     * [afterRenewal](방금 갱신이 Renewed)인데 401이면 [countUnauthorizedAfterRenewal]로 세고, 상한이면 닫는다(AN6-3).
     */
    private suspend fun tryConnect(afterRenewal: Boolean = false): Boolean = try {
        connectOrThrow()
        false
    } catch (e: HandshakeFailed) {
        val isUnauthorized = e.code == UNAUTHORIZED
        if (afterRenewal && isUnauthorized && countUnauthorizedAfterRenewal()) {
            closeForGood(AppError.Unauthorized)
            false
        } else {
            isUnauthorized
        }
    } catch (e: IOException) {
        false
    }

    /** 갱신 직후 401을 하나 센다. 상한([RENEWED_UNAUTHORIZED_LIMIT])에 이르렀으면 참. */
    private fun countUnauthorizedAfterRenewal(): Boolean = synchronized(lock) {
        unauthorizedAfterRenewal++
        unauthorizedAfterRenewal >= RENEWED_UNAUTHORIZED_LIMIT
    }

    internal companion object {
        const val NORMAL_CLOSURE = 1000
        const val POLICY_VIOLATION = 1008
        const val SERVICE_RESTARTED = 1012
        const val TOKEN_EXPIRED = 4401
        const val UNAUTHORIZED = 401
        const val BUFFER = 64
        const val PING_INTERVAL_SECONDS = 10L

        /** D-16: 1·2·4초 뒤, 이후 5초 간격. */
        val RECONNECT_DELAYS_MILLIS = listOf(1_000L, 2_000L, 4_000L)
        const val RECONNECT_STEADY_MILLIS = 5_000L

        /** D-153 (2): 곧바로 재연결은 1분 안에 3번째부터 일반 간격으로 넘긴다. */
        const val RAPID_LIMIT = 3
        const val RAPID_WINDOW_MILLIS = 60_000L

        /** AN6-3: 갱신은 되는데 핸드셰이크가 401인 재연결은 연결 없이 3번까지만(D-153 (2)의 3번과 같은 상한). */
        const val RENEWED_UNAUTHORIZED_LIMIT = 3

        /** 끊긴 연결을 빨리 알아채도록 핑을 보낸다(대결 진행도 지연 3초 목표, NFR-005). */
        fun pinging(client: OkHttpClient): OkHttpClient =
            client.newBuilder().pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS).build()
    }
}
