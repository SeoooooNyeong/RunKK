package com.a207.runkk.app.data.live

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.ProgressReport
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 실시간 채널을 고른다(6.1.14 data.live, D-57). 서버 설정을 받지 않고 연결 결과로 고른다:
 * WebSocket을 먼저 열고, 핸드셰이크가 404·426이면 바로, 그 밖의 실패가 3번 이어지면(사이 1·2초, D-16) 폴링 채널로 바꾼다.
 *
 * open()은 고른 결과를 감싼 채널 하나를 돌려주고, 닫히기 전까지 같은 채널을 돌려준다(매칭 화면과 BattleRunCoordinator가
 * 같은 연결을 쓴다). connect()는 여러 번 불러도 한 번만 연결한다. 감싼 채널의 messages는 connect 전에 구독해도 고른 채널의
 * 메시지를 빠짐없이 받는다(고른 채널의 구독을 연결 전에 시작한다).
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
    private var current: Selected? = null

    override fun open(): LiveChannel = synchronized(lock) {
        current?.takeUnless { it.isClosed } ?: Selected().also { current = it }
    }

    /** 고른 채널(WebSocket 또는 폴링)에 일을 넘기는 감싼 채널. 밖에서는 LiveChannel로만 보인다. */
    private inner class Selected : LiveChannel {
        private val connectLock = Mutex()
        private val stateLock = Any()
        private var delegate: LiveChannel? = null
        private var forwarding: Job? = null

        @Volatile
        var isClosed = false
            private set

        private val incoming =
            MutableSharedFlow<ServerMessage>(
                extraBufferCapacity = BUFFER,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        override val messages: Flow<ServerMessage> = incoming.asSharedFlow()

        /** WebSocket 또는 폴링 가운데 하나로 연결한다. 폴링으로 떨어지면 늘 연결되므로 닫힌 뒤가 아니면 성공이다. */
        override suspend fun connect(): Outcome<Unit> = connectLock.withLock {
            if (isClosed) return@withLock Outcome.Failure(AppError.Network)
            if (synchronized(stateLock) { delegate } != null) return@withLock Outcome.Success(Unit)
            val chosen = choose()
            synchronized(stateLock) {
                if (isClosed) chosen.close() else delegate = chosen
            }
            if (isClosed) Outcome.Failure(AppError.Network) else Outcome.Success(Unit)
        }

        override fun send(m: ProgressReport) {
            synchronized(stateLock) { delegate }?.send(m)
        }

        override fun close() {
            val closing =
                synchronized(stateLock) {
                    isClosed = true
                    forwarding?.cancel()
                    delegate.also { delegate = null }
                }
            closing?.close()
            synchronized(lock) { if (current === this) current = null }
        }

        private suspend fun choose(): LiveChannel {
            repeat(HANDSHAKE_ATTEMPTS) { attempt ->
                val socket = webSockets.get()
                val forward = forward(socket)
                try {
                    socket.connectOrThrow()
                    return socket
                } catch (e: WebSocketLiveChannel.HandshakeFailed) {
                    forward.cancel()
                    socket.close()
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

        /** 고른 채널의 메시지를 연결 전에 구독해 이 채널로 넘긴다. */
        private fun forward(source: LiveChannel): Job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) { source.messages.collect(incoming::emit) }
                .also { job -> synchronized(stateLock) { forwarding = job } }
    }

    private companion object {
        const val HANDSHAKE_ATTEMPTS = 3
        const val BUFFER = 64

        /** 서버에 /ws가 없거나(404, runkk.live=polling) 업그레이드를 거부함(426). */
        val FALLBACK_STATUS = setOf(404, 426)

        /** 핸드셰이크 재시도 간격(D-16의 1·2초). */
        val RETRY_DELAYS_MILLIS = listOf(1_000L, 2_000L)
    }
}
