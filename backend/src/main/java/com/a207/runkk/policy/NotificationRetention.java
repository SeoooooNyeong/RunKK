package com.a207.runkk.policy;

import java.time.LocalDateTime;

/**
 * 알림 보관 일수(설계 6.1.9).
 *
 * @param days 보관 일수
 */
public record NotificationRetention(int days) {

    /** occurredAt + days < now. */
    public boolean isExpired(LocalDateTime occurredAt, LocalDateTime now) {
        return occurredAt.plusDays(days).isBefore(now);
    }
}
