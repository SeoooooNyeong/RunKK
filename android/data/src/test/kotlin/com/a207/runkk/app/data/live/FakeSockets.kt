package com.a207.runkk.app.data.live

import java.io.IOException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * 시험용 WebSocket 공장: 실제 소켓 없이 핸드셰이크·서버 메시지·서버 닫기를 시험이 직접 일으킨다(가상 시계와 함께 쓴다).
 * [handshake]가 null이면 업그레이드 성공(바로 onOpen), 숫자면 그 HTTP 응답 코드로 핸드셰이크 실패다.
 */
class FakeSockets : WebSocket.Factory {
    var handshake: () -> Int? = { null }
    val opened = mutableListOf<FakeSocket>()
    val attempts: Int get() = attemptCount
    private var attemptCount = 0

    val last: FakeSocket get() = opened.last()

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        attemptCount++
        val socket = FakeSocket(request, listener)
        when (val failure = handshake()) {
            null -> {
                opened += socket
                listener.onOpen(socket, response(request, 101))
            }

            else -> listener.onFailure(socket, IOException("handshake $failure"), response(request, failure))
        }
        return socket
    }

    private fun response(request: Request, code: Int): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("").build()

    class FakeSocket(private val request: Request, private val listener: WebSocketListener) : WebSocket {
        val sent = mutableListOf<String>()
        var closedByApp: Int? = null
            private set

        override fun request(): Request = request

        override fun queueSize(): Long = 0

        override fun send(text: String): Boolean {
            sent += text
            return true
        }

        override fun send(bytes: ByteString): Boolean = false

        override fun close(code: Int, reason: String?): Boolean {
            closedByApp = code
            return true
        }

        override fun cancel() = Unit

        /** 서버가 보낸 문자 메시지. */
        fun receive(text: String) = listener.onMessage(this, text)

        /** 서버가 닫기 코드로 닫는다(OkHttp처럼 onClosing 뒤 onClosed). */
        fun closeFromServer(code: Int) {
            listener.onClosing(this, code, "")
            listener.onClosed(this, code, "")
        }

        /** 연결이 끊긴다(닫기 코드 없음). */
        fun drop() = listener.onFailure(this, IOException("drop"), null)
    }
}
