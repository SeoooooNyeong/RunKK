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

/**
 * 실시간 채널을 고른다(D-57). 구현 data.live.FallbackLiveChannelProvider: WebSocket을 먼저 열고 핸드셰이크가
 * 404·426이거나 3번 연속 실패하면 폴링 채널을 돌려준다. 매칭 화면(ui.battle)과 BattleRunCoordinator가
 * 같은 연결을 쓰도록, 구현은 닫히기 전까지 같은 채널을 돌려준다.
 */
interface LiveChannelProvider {
    fun open(): LiveChannel
}
