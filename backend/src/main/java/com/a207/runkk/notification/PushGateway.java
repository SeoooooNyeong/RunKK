package com.a207.runkk.notification;

import java.util.List;

/**
 * 외부 푸시 발송 포트(설계 6.1.10, D-43). 구현 infra.integration.push.FcmPushGateway는 받은 토큰으로 보내기만 하고 토큰 표를 갖지
 * 않는다. 회원 ID를 받지 않는다.
 */
public interface PushGateway {

    PushResult send(List<String> deviceTokens, NotificationType type, String content);

    /**
     * 제목을 정해 보낸다(운영자 공지, S15P31A207-275). title이 null이면 send와 같다. 기본 구현은 제목을 버리고 send를 부르므로 제목을 보여야
     * 하는 구현(FcmPushGateway)은 고쳐 쓴다.
     */
    default PushResult send(
            List<String> deviceTokens, NotificationType type, String title, String content) {
        return send(deviceTokens, type, content);
    }

    /** send와 같되 같은 collapseKey의 기기 알림을 하나로 묶는다(CREW_CHAT, v0.11 D-102). 기기 알림 화면이 메시지마다 쌓이지 않는다. */
    PushResult sendCollapsing(
            List<String> deviceTokens, NotificationType type, String content, String collapseKey);
}
