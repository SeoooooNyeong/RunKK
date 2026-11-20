package com.a207.runkk.app.domain.crew

/** [CrewRunSignals.watch]가 내보내는 값: 변경 신호([Changed]) 또는 실시간 연결 여부([Link], CONNECTED만 참). */
sealed interface CrewRunSignal {
    data class Changed(val change: CrewChange) : CrewRunSignal

    data class Link(val isConnected: Boolean) : CrewRunSignal
}
