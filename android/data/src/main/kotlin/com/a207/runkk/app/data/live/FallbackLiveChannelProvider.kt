package com.a207.runkk.app.data.live

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.ClientMessage
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.ServerMessage
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 실시간 연결 하나를 임대로 나눠 준다(6.1.14 data.live, D-57, D-103, D-131).
 *
 * 서버는 회원마다 연결 하나만 두고 새 연결이 이전 연결을 닫으므로 대결·크루런·채팅이 연결 하나를 함께 쓴다. open()은 지금 연결에 대한
 * 새 임대를 돌려주고(참조 수 +1), 임대의 close()는 자기 임대만 반납한다(두 번 불러도 한 번). 참조 수가 0이 될 때만 실제 연결을 닫고,
 * 그 뒤 open()은 새 연결을 만든다. 받기 Flow는 임대들이 함께 받고 반납한 임대의 Flow는 끝난다. 상태(state)는 연결의 상태다.
 *
 * 연결 방식은 서버 설정을 받지 않고 연결 결과로 고른다: WebSocket을 먼저 열고, 핸드셰이크가 404·426이면 바로, 그 밖의 실패가 3번
 * 이어지면(사이 1·2초, D-16) 폴링 채널로 바꾼다. 핸드셰이크 401(인증 실패)은 인증된 OkHttp의 TokenAuthenticator가 접근 토큰을 갱신해
 * 한 번 다시 보낸다. 그래도 401이면 갱신 토큰까지 거부된 것이므로 폴링으로 바꾸지 않고 connect가 Failure(Unauthorized)로 끝난다.
 * 고른 채널이 스스로 닫히면(1000 새 연결, 1008·4401 갱신 거부) 연결을 끝난 것으로 보고 다음 open()이 새 연결을 만든다.
 */
@Singleton
class FallbackLiveChannelProvider
@Inject
constructor(
    private val webSockets: Provider<WebSocketLiveChannel>,
    private val pollers: Provider<PollingLiveChannel>,
    private val scope: CoroutineScope
) : LiveChannelProvider {
    private val lock = Any()
    private var current: Connection? = null

    /**
     * 지금 연결의 임대를 돌려준다. 끝났는지 확인과 참조 수 증가는 [Connection.tryLease] 한 번에 해서, 다른 스레드가 마지막 임대를
     * 반납해 연결이 끝나는 것과 겹쳐도 끝난 연결을 임대하지 않는다(그때는 새 연결을 만든다).
     */
    override fun open(): LiveChannel = synchronized(lock) {
        current?.tryLease() ?: Connection().also { current = it }.lease()
    }

    /** 실제 연결 하나(고른 WebSocket 또는 폴링)와 그 임대 참조 수. */
    private inner class Connection {
        private val connectLock = Mutex()
        private val stateLock = Any()
        private var delegate: LiveChannel? = null
        private val jobs = mutableListOf<Job>()
        private var leases = 0

        /** 실제 연결을 닫았거나 고른 채널이 스스로 닫혔다. stateLock 안에서만 바꾼다. */
        @Volatile
        var isEnded = false
            private set

        val incoming =
            MutableSharedFlow<ServerMessage>(
                extraBufferCapacity = BUFFER,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

        private val connection = MutableStateFlow(LiveState.CONNECTING)
        val state: StateFlow<LiveState> = connection.asStateFlow()

        /** 끝나지 않았을 때만 임대한다(확인과 증가를 stateLock 한 번에). 끝났으면 null. */
        fun tryLease(): LiveChannel? = synchronized(stateLock) {
            if (isEnded) {
                null
            } else {
                leases++
                Lease(this)
            }
        }

        /** 막 만든 연결의 첫 임대. 아직 누구도 임대하지 않아 끝났을 수 없다. */
        fun lease(): LiveChannel = checkNotNull(tryLease()) { "새 연결은 끝나지 않았다" }

        /** 임대 하나를 돌려받는다. 마지막 임대면 실제 연결을 닫는다. */
        fun release() {
            val closing =
                synchronized(stateLock) {
                    leases--
                    if (leases > 0) return
                    end()
                }
            closing?.close()
            synchronized(lock) { if (current === this) current = null }
        }

        /** WebSocket 또는 폴링 가운데 하나로 연결한다. 이미 연결됐으면 바로 성공이다(실제 핸드셰이크는 연결마다 한 번). */
        suspend fun connect(): Outcome<Unit> = connectLock.withLock {
            if (isEnded) return@withLock Outcome.Failure(AppError.Network)
            if (synchronized(stateLock) { delegate } != null) return@withLock Outcome.Success(Unit)
            val chosen = choose() ?: return@withLock Outcome.Failure(AppError.Unauthorized)
            val attached =
                synchronized(stateLock) {
                    if (isEnded) {
                        false
                    } else {
                        delegate = chosen
                        true
                    }
                }
            if (!attached) {
                chosen.close()
                synchronized(stateLock) {
                    jobs.forEach(Job::cancel)
                    jobs.clear()
                }
                return@withLock Outcome.Failure(AppError.Network)
            }
            follow(chosen)
            Outcome.Success(Unit)
        }

        fun send(m: ClientMessage) {
            synchronized(stateLock) { delegate }?.send(m)
        }

        /** 끝낸다(참조 수 0). 고른 채널을 돌려주어 lock 밖에서 닫게 한다. stateLock 안에서만. */
        private fun end(): LiveChannel? {
            isEnded = true
            connection.value = LiveState.CLOSED
            jobs.forEach(Job::cancel)
            jobs.clear()
            return delegate.also { delegate = null }
        }

        /** 고른 채널의 상태를 이 연결의 상태로 옮긴다. 스스로 닫히면(CLOSED) 연결을 끝낸다. */
        private fun follow(chosen: LiveChannel) {
            val job =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    chosen.state.collect { delegateState ->
                        connection.value = delegateState
                        if (delegateState == LiveState.CLOSED) {
                            synchronized(stateLock) { if (delegate === chosen) end() }
                            synchronized(lock) { if (current === this@Connection) current = null }
                        }
                    }
                }
            synchronized(stateLock) { if (isEnded) job.cancel() else jobs += job }
        }

        /** WebSocket 또는 폴링. 토큰 갱신 뒤에도 인증에 실패하면(401) null. */
        private suspend fun choose(): LiveChannel? {
            repeat(HANDSHAKE_ATTEMPTS) { attempt ->
                val socket = webSockets.get()
                val forward = forward(socket)
                try {
                    socket.connectOrThrow()
                    return socket
                } catch (e: WebSocketLiveChannel.HandshakeFailed) {
                    forward.cancel()
                    synchronized(stateLock) { jobs -= forward }
                    socket.close()
                    if (e.code == UNAUTHORIZED) return null
                    if (e.code in FALLBACK_STATUS) return polling()
                }
                if (attempt < HANDSHAKE_ATTEMPTS - 1) delay(RETRY_DELAYS_MILLIS[attempt])
            }
            return polling()
        }

        private suspend fun polling(): LiveChannel = pollers.get().also { poller ->
            forward(poller)
            poller.connect()
        }

        /** 고른 채널의 메시지를 연결 전에 구독해 이 연결로 넘긴다. */
        private fun forward(source: LiveChannel): Job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) { source.messages.collect(incoming::emit) }
                .also { job -> synchronized(stateLock) { if (isEnded) job.cancel() else jobs += job } }
    }

    /** 연결 하나에 대한 임대(D-103). 반납한 뒤에는 보내지 않고 받기 Flow가 끝난다. */
    private class Lease(private val connection: Connection) : LiveChannel {
        private val released = MutableStateFlow(false)

        override val messages: Flow<ServerMessage> =
            channelFlow {
                if (released.value) return@channelFlow
                val receiving = launch(start = CoroutineStart.UNDISPATCHED) { connection.incoming.collect { send(it) } }
                released.first { it }
                receiving.cancel()
            }

        override val state: StateFlow<LiveState> = connection.state

        override suspend fun connect(): Outcome<Unit> =
            if (released.value) Outcome.Failure(AppError.Network) else connection.connect()

        override fun send(m: ClientMessage) {
            if (!released.value) connection.send(m)
        }

        override fun close() {
            if (released.compareAndSet(expect = false, update = true)) connection.release()
        }
    }

    private companion object {
        const val HANDSHAKE_ATTEMPTS = 3
        const val BUFFER = 64

        /** 토큰 갱신(TokenAuthenticator) 뒤에도 인증 실패. */
        const val UNAUTHORIZED = 401

        /** 서버에 /ws가 없거나(404, runkk.live=polling) 업그레이드를 거부함(426). */
        val FALLBACK_STATUS = setOf(404, 426)

        /** 핸드셰이크 재시도 간격(D-16의 1·2초). */
        val RETRY_DELAYS_MILLIS = listOf(1_000L, 2_000L)
    }
}
