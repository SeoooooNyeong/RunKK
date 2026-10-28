package com.a207.runkk.app.domain.live

/** 실시간 연결 상태(D-131). POLLING은 WebSocket을 쓸 수 없어 폴링으로 대신하는 상태다(D-57). */
enum class LiveState {
    CONNECTING,
    CONNECTED,
    POLLING,
    CLOSED
}
