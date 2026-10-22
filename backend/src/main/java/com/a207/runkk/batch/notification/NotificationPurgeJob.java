package com.a207.runkk.batch.notification;

import com.a207.runkk.notification.NotificationService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 3시에 보관 기한 지난 알림 삭제를 부른다(설계 6.1.13 batch.notification, FR-175). NotificationService.purgeExpired
 * 하나만 부른다(D-39). 20시 계획 미이행 알림(PlanReminderJob)은 analysis를 부르므로 batch.analysis 단계에서 만든다.
 */
@Component
public class NotificationPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationPurgeJob.class);

    private final NotificationService notificationService;
    private final Clock clock;

    public NotificationPurgeJob(NotificationService notificationService, Clock clock) {
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public void run() {
        try {
            notificationService.purgeExpired(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("notification_purge_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
