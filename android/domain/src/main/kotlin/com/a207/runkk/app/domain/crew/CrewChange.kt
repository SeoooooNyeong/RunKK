package com.a207.runkk.app.domain.crew

/**
 * 크루런 변경 사실(설계 05-32 CrewRunChanged를 ui.crew가 보는 값으로 옮긴 것). [resource] 화면의 원본을 다시 읽으라는 신호이며
 * 값은 담지 않는다. ui.crew는 domain.live를 보지 않으므로(5.1.2) CrewRunSignals가 이 값으로 바꿔 준다.
 */
data class CrewChange(val crewId: Long, val postId: Long, val resource: Resource, val revision: Long) {
    /** 다시 읽을 화면: 크루런 목록, 대기실, 진행 현황, 결과. */
    enum class Resource {
        SCHEDULE,
        LOBBY,
        PROGRESS,
        RESULT
    }
}
