package com.a207.runkk.notification;

/**
 * 알림을 눌렀을 때 여는 화면 종류(자료사전 이동대상, FR-178 "코치 현황, 아바타, 크루 관리, 크루 홈, 크루런 모집, 대결 결과"). 설계 6.1.10이
 * NavigationTarget.screen의 형식으로만 적은 열거형이다(B2 결정).
 */
public enum Screen {
    COACH_PROGRESS,
    AVATAR,
    CREW_MANAGE,
    CREW_HOME,
    CREW_RUN_RECRUIT,
    BATTLE_RESULT,
    // v0.11 (FR-178, D-150: CREW_BOARD는 가는 알림종류가 없어 두지 않는다)
    CREW_NOTICE,
    CREW_POLL,
    CREW_SCHEDULE,
    CREW_CHAT,
    AVATAR_BACKGROUND
}
