package com.a207.runkk.notification;

import java.util.List;

/**
 * 외부 푸시 발송 포트(설계 6.1.10, D-43). 구현 infra.integration.push.FcmPushGateway는 받은 토큰으로 보내기만 하고 토큰 표를 갖지
 * 않는다. 회원 ID를 받지 않는다.
 */
public interface PushGateway {

    PushResult send(List<String> deviceTokens, NotificationType type, String content);
}
