package com.a207.runkk.app.domain.testing

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.ChatPresence
import com.a207.runkk.app.domain.live.ClientMessage
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLiveChannel : LiveChannel {
    /** 보낸 진행값. */
    val sent = mutableListOf<ProgressReport>()

    /** 보낸 채팅방 입장·퇴장 신호. */
    val presences = mutableListOf<ChatPresence>()

    /** 보내기·반납 순서("send:…", "close"). */
    val events = mutableListOf<String>()
    var connects = 0
    var closed = false
    var connectFailure: AppError? = null

    /** 있으면 connect가 이것이 끝날 때까지 기다린다(핸드셰이크가 늦은 연결). */
    var connectGate: CompletableDeferred<Unit>? = null
    override val messages = MutableSharedFlow<ServerMessage>()
    override val state = MutableStateFlow(LiveState.CONNECTED)

    override suspend fun connect(): Outcome<Unit> {
        connects++
        connectGate?.await()
        return connectFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override fun send(m: ClientMessage) {
        events += "send:$m"
        when (m) {
            is ProgressReport -> sent += m
            is ChatPresence -> presences += m
        }
    }

    override fun close() {
        events += "close"
        closed = true
    }
}

class FakeLiveChannelProvider(val channel: FakeLiveChannel = FakeLiveChannel()) : LiveChannelProvider {
    var opens = 0

    override fun open(): LiveChannel {
        opens++
        return channel
    }
}
