package com.a207.runkk.app.data.live

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.live.LiveApi
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val scope: CoroutineScope
) : LiveChannel {
    private val decoder = ServerMessageDecoder()
    private val lock = Any()
    private var polling: Job? = null
    private var closed = false

    private val incoming =
        MutableSharedFlow<ServerMessage>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val messages: Flow<ServerMessage> = incoming.asSharedFlow()

    override suspend fun connect() {
        synchronized(lock) {
            if (closed || polling != null) return
            polling = scope.launch { pollEverySecond() }
        }
    }

    override fun send(m: ProgressReport) {
        if (synchronized(lock) { closed }) return
        val body = m.toJsonObject()
        scope.launch { ApiErrorMapper.call { api.progress(body) } }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            polling?.cancel()
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
