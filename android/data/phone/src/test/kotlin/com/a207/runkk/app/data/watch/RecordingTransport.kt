package com.a207.runkk.app.data.watch

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 시험용 Data Layer 송수신: 보낸 것을 일어난 차례대로 남긴다(WatchSessionPublisher의 보내는 순서 시험, D-390).
 * [isReachable]이 거짓이면 바로 보내기(sendNow)가 실패한다(워치에 닿지 않음).
 */
internal class RecordingTransport : WatchLink.Transport {
    /** 일어난 일(종류, 경로, 내용). 종류는 send·sendNow·put·delete다. */
    val events = mutableListOf<Event>()
    override val connected = MutableStateFlow(true)
    override val incoming = MutableSharedFlow<WatchLink.Incoming>(extraBufferCapacity = 16)
    var isReachable = true

    class Event(val kind: String, val path: String, val payload: ByteArray?, val nodeId: String? = null)

    override fun send(path: String, payload: ByteArray) {
        events += Event("send", path, payload)
    }

    override suspend fun sendNow(path: String, payload: ByteArray, nodeId: String?): Boolean {
        if (!isReachable) return false
        events += Event("sendNow", path, payload, nodeId)
        return true
    }

    override suspend fun putItem(path: String, payload: ByteArray): Boolean {
        events += Event("put", path, payload)
        return true
    }

    override suspend fun deleteItems(path: String): Boolean {
        events += Event("delete", path, null)
        return true
    }

    fun receive(path: String, payload: ByteArray, nodeId: String? = null) {
        check(incoming.tryEmit(WatchLink.Incoming(path, payload, nodeId)))
    }

    fun of(kind: String, path: String): List<Event> = events.filter { it.kind == kind && it.path == path }
}
