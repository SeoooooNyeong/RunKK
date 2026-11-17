package com.a207.runkk.crew;

/**
 * 크루런 화면의 논리 단계(설계 05-32 2.3, 5.2 LobbyResponse.phase). 상태 enum(CrewRunStatus)은 그대로 두고 대기실의 카운트다운만
 * 나눠 보인다. 투표 중(VOTING)도 LOBBY로 보인다.
 */
public enum CrewRunPhase {
    LOBBY,
    COUNTDOWN,
    RUNNING,
    FINISHED,
    CANCELLED
}
