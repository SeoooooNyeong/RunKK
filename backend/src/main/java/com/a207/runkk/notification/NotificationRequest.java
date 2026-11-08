package com.a207.runkk.notification;

/**
 * 알림 사건(자료사전 알림사건, 설계 6.1.10). 발생 서비스가 NotificationService.notify에 넘긴다.
 *
 * @param recipientId 수신자 회원 ID
 * @param type 알림 종류
 * @param content 알림 내용, 비지 않음
 * @param target 이동 대상, 없으면 종류의 기본 화면
 * @param title 알림 제목(운영자 공지만, S15P31A207-275). 없으면 null이고 앱이 종류로 제목을 정하며 푸시 제목은 「런크크」다
 */
public record NotificationRequest(
        long recipientId,
        NotificationType type,
        String content,
        NavigationTarget target,
        String title) {

    public NotificationRequest {
        if (type == null) {
            throw new IllegalArgumentException("알림 종류가 비었다");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("알림 내용이 비었다");
        }
        if (title != null && title.isBlank()) {
            throw new IllegalArgumentException("알림 제목이 비었다");
        }
    }

    /** 제목 없는 알림 사건(운영자 공지 밖의 모든 종류). */
    public NotificationRequest(
            long recipientId, NotificationType type, String content, NavigationTarget target) {
        this(recipientId, type, content, target, null);
    }
}
