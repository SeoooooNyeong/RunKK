package com.a207.runkk.app.domain.testing

import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeLiveChannel : LiveChannel {
    val sent = mutableListOf<ProgressReport>()
    var connects = 0
    var closed = false
    var connectFailure: Exception? = null
    override val messages = MutableSharedFlow<ServerMessage>()

    override suspend fun connect() {
        connects++
        connectFailure?.let { throw it }
    }

    override fun send(m: ProgressReport) {
        sent += m
    }

    override fun close() {
        closed = true
    }
}

class FakeLiveChannelProvider(val channel: FakeLiveChannel = FakeLiveChannel()) : LiveChannelProvider {
    override fun open(): LiveChannel = channel
}
