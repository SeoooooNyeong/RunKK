package com.a207.runkk.app.ui.crew

/**
 * 중계 속 크루원 상태(서버 RelayEntry.State와 같은 이름): 달리는 중, 일시정지, 연결 확인 중, 끝냄. ui.crew는 domain.live를 보지 않으므로
 * (5.1.2) 조정자 상태의 이름으로 옮겨 쓴다.
 */
enum class MateState {
    RUNNING,
    PAUSED,
    CHECKING,
    ENDED;

    companion object {
        /** 이름으로 고른다. 모르는 이름은 달리는 중으로 본다. */
        fun of(name: String): MateState = entries.firstOrNull { it.name == name } ?: RUNNING
    }
}
