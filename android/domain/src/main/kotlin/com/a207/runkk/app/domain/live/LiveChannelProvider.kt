package com.a207.runkk.app.domain.live

import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * 실시간 채널을 고른다(D-57). 구현 data.live.FallbackLiveChannelProvider: WebSocket을 먼저 열고 핸드셰이크가
 * 404·426이거나 3번 연속 실패하면 폴링 채널을 돌려준다. 매칭 화면(ui.battle)과 BattleRunCoordinator가
 * 같은 연결을 쓰도록, 구현은 닫히기 전까지 같은 채널을 돌려준다.
 */
interface LiveChannelProvider {
    fun open(): LiveChannel
}
