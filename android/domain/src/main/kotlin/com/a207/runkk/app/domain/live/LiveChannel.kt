package com.a207.runkk.app.domain.live

import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 실시간 메시지 송수신 포트(D-06, D-57). LiveChannelProvider.open()이 돌려주는 연결 하나에 대한 임대다(D-103).
 * 구현 data.live(WebSocketLiveChannel·PollingLiveChannel을 FallbackLiveChannelProvider가 임대로 감싼다).
 * 어떤 메시지에도 위치 필드가 없다(FR-089, NFR-021).
 */
interface LiveChannel {
    /** 연결한다. 실패(핸드셰이크 거부·네트워크)는 예외가 아니라 Failure(AppError)로 돌려준다(13.2). */
    suspend fun connect(): Outcome<Unit>

    /** 앱 → 서버 메시지(진행값, 채팅방 입장·퇴장). */
    fun send(m: ClientMessage)

    /** 서버 → 앱 메시지. 임대들이 함께 받고 쓰는 쪽(조정자, ChatRoom)이 필요한 메시지만 거른다(D-103). */
    val messages: Flow<ServerMessage>

    /** 연결 상태. 재연결·폴링 전환을 알린다(ChatRoom의 메우기·입장 재전송 근거, D-131). */
    val state: StateFlow<LiveState>

    /** 자기 임대만 반납한다. 참조 수가 0이 될 때만 실제 연결을 닫는다(D-103). */
    fun close()
}
