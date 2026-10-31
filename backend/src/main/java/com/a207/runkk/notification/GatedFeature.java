package com.a207.runkk.notification;

/**
 * 1차 배포에서 닫힌 기능(D138). 딸린 알림 종류(NotificationType.gatedFeature)는 그 기능의 운영 기준 공개 스위치(CREW_RELEASED·
 * AVATAR_RELEASED)가 켜질 때까지 알림함에만 저장하고 푸시하지 않는다. 앱 domain.notification.GatedFeature와 같은 이름·분류이고, 계약
 * 파일 policy/GatedNotification.txt로 대조한다(H-47).
 */
public enum GatedFeature {
    /** 아바타 꾸미기(체형·배경 알림). 운영 기준 AVATAR_RELEASED. */
    AVATAR_CUSTOMIZE,
    /** 크루(가입·크루런·공지·투표·일정·채팅·공동 목표·신고 처리 알림). 운영 기준 CREW_RELEASED. */
    CREW
}
