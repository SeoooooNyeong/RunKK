package com.a207.runkk.watch.testing

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.ClientMessage
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.ServerMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 실시간 채널(가짜). 모든 임대가 같은 메시지 흐름을 받는다. [isConnectFailing]이면 연결이 Network 실패다. */
class FakeLiveChannelProvider : LiveChannelProvider {
    val server = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    var isConnectFailing: Boolean = false
    var opened: Int = 0
        private set
    var closed: Int = 0
        private set

    override fun open(): LiveChannel {
        opened++
        return object : LiveChannel {
            override suspend fun connect(): Outcome<Unit> =
                if (isConnectFailing) Outcome.Failure(AppError.Network) else Outcome.Success(Unit)

            override fun send(m: ClientMessage) = Unit

            override val messages = server

            override val state: StateFlow<LiveState> = MutableStateFlow(LiveState.CONNECTED)

            override fun close() {
                closed++
            }
        }
    }
}
