package com.a207.runkk.notification;

/**
 * 알림 사건(자료사전 알림사건, 설계 6.1.10). 발생 서비스가 NotificationService.notify에 넘긴다.
 *
 * @param recipientId 수신자 회원 ID
 * @param type 알림 종류
 * @param content 알림 내용, 비지 않음
 * @param target 이동 대상, 없으면 종류의 기본 화면
 */
public record NotificationRequest(
        long recipientId, NotificationType type, String content, NavigationTarget target) {

    public NotificationRequest {
        if (type == null) {
            throw new IllegalArgumentException("알림 종류가 비었다");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("알림 내용이 비었다");
        }
    }
}
