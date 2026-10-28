package com.a207.runkk.policy;

/**
 * CREW_RUN_LOBBY_OPEN 값의 형식(1단계 리뷰 L4, 설계 갱신 요청 U2). OperationPolicy.crewRunRule이 CrewRunEnd와 합쳐
 * CrewRunRule을 만든다. policy 안에서만 쓴다.
 *
 * @param lobbyOpenBeforeMinutes 출발 전 대기실 개방(분)
 * @param reminderBeforeMinutes 출발 전 알림(분)
 * @param minReady 최소 준비 인원
 */
record CrewRunLobby(int lobbyOpenBeforeMinutes, int reminderBeforeMinutes, int minReady) {

    boolean allPositive() {
        return lobbyOpenBeforeMinutes > 0 && reminderBeforeMinutes > 0 && minReady > 0;
    }
}
