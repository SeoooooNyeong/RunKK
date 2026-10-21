package com.a207.runkk.notification;

import java.util.List;

/**
 * 푸시 발송 결과(설계 6.1.10, D-43). 무효 토큰은 NotificationService가 지운다.
 *
 * @param sentCount 보낸 수
 * @param invalidTokens 제공자가 무효라고 답한 토큰
 */
public record PushResult(int sentCount, List<String> invalidTokens) {

    public PushResult {
        invalidTokens = invalidTokens == null ? List.of() : List.copyOf(invalidTokens);
    }

    public static PushResult none() {
        return new PushResult(0, List.of());
    }
}
