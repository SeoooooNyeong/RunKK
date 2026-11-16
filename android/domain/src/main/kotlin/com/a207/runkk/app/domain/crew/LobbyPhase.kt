package com.a207.runkk.app.domain.crew

/**
 * 크루런 대기실의 논리 단계(서버 crew.CrewRunPhase, 설계 05-32 2.3). status(LOBBY_OPEN 등)는 그대로 두고 대기실 응답에 함께 온다.
 * 투표 중도 LOBBY다. COUNTDOWN은 출발 예약(후보 고정, 3초)이 잡힌 대기실이다.
 */
enum class LobbyPhase {
    LOBBY,
    COUNTDOWN,
    RUNNING,
    FINISHED,
    CANCELLED
}
