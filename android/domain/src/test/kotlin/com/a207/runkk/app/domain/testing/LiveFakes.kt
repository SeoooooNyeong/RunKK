package com.a207.runkk.app.domain.testing

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeLiveChannel : LiveChannel {
    val sent = mutableListOf<ProgressReport>()
    var connects = 0
    var closed = false
    var connectFailure: AppError? = null
    override val messages = MutableSharedFlow<ServerMessage>()

    override suspend fun connect(): Outcome<Unit> {
        connects++
        return connectFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
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
