package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.CrewRunRelay

/** 중계 한 줄(합산 방식): 크루원 표시명·누적 거리·상태. 위치는 없다. [isMe]는 내 줄이다. */
data class RelayRow(
    val name: String,
    val distance: Distance,
    val state: CrewRunRelay.State = CrewRunRelay.State.RUNNING,
    val isMe: Boolean = false
) {
    /** 아직 달리는(정지·연결 확인 중 포함) 크루원인가. 끝낸 사람은 지도와 "함께 달리는 크루원"에서 뺀다. */
    val isRunning: Boolean get() = state != CrewRunRelay.State.ENDED
}
