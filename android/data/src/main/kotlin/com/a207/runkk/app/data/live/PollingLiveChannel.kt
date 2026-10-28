package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.live.LiveApi
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.ClientMessage
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 폴링 실시간 채널(6.1.14 data.live, C-004 대체, design-review.md E5). 1초마다 api.live.LiveApi로 GET /live/poll을 불러
 * 쌓인 메시지를 내보내고, 진행값은 POST /live/progress로 보낸다. 실패한 폴링·전송은 버리고 다음 주기에 이어 간다.
 */
class PollingLiveChannel
@Inject
constructor(
    private val api: LiveApi,
    private val scope: CoroutineScope,
    private val decoder: ServerMessageDecoder
) : LiveChannel {
    internal constructor(api: LiveApi, scope: CoroutineScope) : this(api, scope, ServerMessageDecoder())

    private val lock = Any()
    private var polling: Job? = null
    private var closed = false

    private val incoming =
        MutableSharedFlow<ServerMessage>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val messages: Flow<ServerMessage> = incoming.asSharedFlow()

    private val connection = MutableStateFlow(LiveState.CONNECTING)
    override val state: StateFlow<LiveState> = connection.asStateFlow()

    /** 폴링을 시작한다. 폴링 실패는 주기마다 다시 하므로 연결 자체는 늘 성공이다. */
    override suspend fun connect(): Outcome<Unit> {
        synchronized(lock) {
            if (!closed && polling == null) polling = scope.launch { pollEverySecond() }
            if (!closed) connection.value = LiveState.POLLING
        }
        return Outcome.Success(Unit)
    }

    /**
     * 진행값만 POST /live/progress로 보낸다. 채팅방 입장(ChatPresence)은 버린다: 폴링 대체 모드의 서버는 방 입장을 쓰지 않고
     * (PollingChatPresence는 빈 집합) 채팅방은 2초마다 ?after=로 읽는다(F.3.2, D-131).
     */
    override fun send(m: ClientMessage) {
        val progress = m as? ProgressReport ?: return
        if (synchronized(lock) { closed }) return
        val body = progress.toJsonObject()
        scope.launch { ApiErrorMapper.call { api.progress(body) } }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            polling?.cancel()
            connection.value = LiveState.CLOSED
        }
    }

    private suspend fun pollEverySecond() {
        while (scope.isActive) {
            val drained = ApiErrorMapper.call { api.poll() }
            if (drained is Outcome.Success) {
                drained.value.forEach { element -> decoder.decode(element)?.let(incoming::tryEmit) }
            }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 1_000L
        const val BUFFER = 64
    }
}
