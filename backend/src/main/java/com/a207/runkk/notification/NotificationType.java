package com.a207.runkk.notification;

/**
 * 알림 종류 21개(자료사전 알림종류 12개 + v0.11 9개, 부록 B, 06-05). 상수마다 알림을 눌렀을 때 여는 기본 화면을 가진다(FR-178, 6.4). 회원의
 * 종류별 수신 설정은 member가 이 이름(name())으로 보관한다(member → notification 의존 금지, 12.2). 크루·아바타 종류는 딸린 미공개
 * 기능(GatedFeature)을 가진다(D138).
 */
public enum NotificationType {
    PLAN_MISSED(Screen.COACH_PROGRESS),
    BODY_SHAPE_CHANGED(Screen.AVATAR, GatedFeature.AVATAR_CUSTOMIZE),
    JOIN_REQUESTED(Screen.CREW_MANAGE, GatedFeature.CREW),
    JOIN_DECIDED(Screen.CREW_HOME, GatedFeature.CREW),
    JOIN_EXPIRED(Screen.CREW_HOME, GatedFeature.CREW),
    LEADER_TRANSFERRED(Screen.CREW_HOME, GatedFeature.CREW),
    CREW_DELETED(Screen.CREW_HOME, GatedFeature.CREW),
    KICKED(Screen.CREW_HOME, GatedFeature.CREW),
    CREW_RUN_RECRUIT(Screen.CREW_RUN_RECRUIT, GatedFeature.CREW),
    CREW_RUN_SOON(Screen.CREW_RUN_RECRUIT, GatedFeature.CREW),
    OPPONENT_ARRIVED(Screen.BATTLE_RESULT),
    BATTLE_CORRECTED(Screen.BATTLE_RESULT),
    // v0.11 (FR-175, FR-178, FR-218, 06-05)
    CREW_NOTICE(Screen.CREW_NOTICE, GatedFeature.CREW),
    CREW_POLL(Screen.CREW_POLL, GatedFeature.CREW),
    POLL_CLOSING_SOON(Screen.CREW_POLL, GatedFeature.CREW),
    CREW_SCHEDULE(Screen.CREW_SCHEDULE, GatedFeature.CREW),
    CREW_CHAT(Screen.CREW_CHAT, GatedFeature.CREW),
    CREW_GOAL_ACHIEVED(Screen.CREW_HOME, GatedFeature.CREW),
    MANAGER_CHANGED(Screen.CREW_HOME, GatedFeature.CREW),
    BACKGROUND_UNLOCKED(Screen.AVATAR_BACKGROUND, GatedFeature.AVATAR_CUSTOMIZE),
    REPORT_RESOLVED(Screen.CREW_HOME, GatedFeature.CREW);

    private final Screen defaultScreen;
    private final GatedFeature gatedFeature;

    NotificationType(Screen defaultScreen) {
        this(defaultScreen, null);
    }

    NotificationType(Screen defaultScreen, GatedFeature gatedFeature) {
        this.defaultScreen = defaultScreen;
        this.gatedFeature = gatedFeature;
    }

    public Screen defaultScreen() {
        return defaultScreen;
    }

    /** 딸린 미공개 기능(D138). 없으면 null(늘 푸시한다). */
    public GatedFeature gatedFeature() {
        return gatedFeature;
    }
}
