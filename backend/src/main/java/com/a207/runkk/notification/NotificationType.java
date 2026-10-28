package com.a207.runkk.notification;

/**
 * 알림 종류 21개(자료사전 알림종류 12개 + v0.11 9개, 부록 B, 06-05). 상수마다 알림을 눌렀을 때 여는 기본 화면을 가진다(FR-178, 6.4). 회원의
 * 종류별 수신 설정은 member가 이 이름(name())으로 보관한다(member → notification 의존 금지, 12.2).
 */
public enum NotificationType {
    PLAN_MISSED(Screen.COACH_PROGRESS),
    BODY_SHAPE_CHANGED(Screen.AVATAR),
    JOIN_REQUESTED(Screen.CREW_MANAGE),
    JOIN_DECIDED(Screen.CREW_HOME),
    JOIN_EXPIRED(Screen.CREW_HOME),
    LEADER_TRANSFERRED(Screen.CREW_HOME),
    CREW_DELETED(Screen.CREW_HOME),
    KICKED(Screen.CREW_HOME),
    CREW_RUN_RECRUIT(Screen.CREW_RUN_RECRUIT),
    CREW_RUN_SOON(Screen.CREW_RUN_RECRUIT),
    OPPONENT_ARRIVED(Screen.BATTLE_RESULT),
    BATTLE_CORRECTED(Screen.BATTLE_RESULT),
    // v0.11 (FR-175, FR-178, FR-218, 06-05)
    CREW_NOTICE(Screen.CREW_NOTICE),
    CREW_POLL(Screen.CREW_POLL),
    POLL_CLOSING_SOON(Screen.CREW_POLL),
    CREW_SCHEDULE(Screen.CREW_SCHEDULE),
    CREW_CHAT(Screen.CREW_CHAT),
    CREW_GOAL_ACHIEVED(Screen.CREW_HOME),
    MANAGER_CHANGED(Screen.CREW_HOME),
    BACKGROUND_UNLOCKED(Screen.AVATAR_BACKGROUND),
    REPORT_RESOLVED(Screen.CREW_HOME);

    private final Screen defaultScreen;

    NotificationType(Screen defaultScreen) {
        this.defaultScreen = defaultScreen;
    }

    public Screen defaultScreen() {
        return defaultScreen;
    }
}
