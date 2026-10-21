package com.a207.runkk.app.domain.live

import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * 실시간 메시지 송수신 포트(D-06, D-57). 구현 data.live.WebSocketLiveChannel·PollingLiveChannel.
 * 앱 → 서버는 진행값(ProgressReport) 하나뿐이고, 어떤 메시지에도 위치 필드가 없다(FR-089, NFR-021).
 */
interface LiveChannel {
    /** 연결한다. 실패(핸드셰이크 거부·네트워크)는 예외가 아니라 Failure(AppError)로 돌려준다(13.2, 리뷰 갱신 요청 1). */
    suspend fun connect(): Outcome<Unit>

    fun send(m: ProgressReport)

    val messages: Flow<ServerMessage>

    fun close()
}
