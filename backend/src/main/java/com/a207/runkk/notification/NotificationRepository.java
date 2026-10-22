package com.a207.runkk.notification;

import com.a207.runkk.common.DateRange;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 알림 저장 포트(설계 6.1.10). 구현은 infra.notification.JpaNotificationRepository이고 notification 밖에서 쓰지 않는다.
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    /** 수신자의 알림, 최신순. 기간이 있으면 발생일이 그 안인 것만. */
    List<Notification> findByRecipient(long recipientId, Optional<DateRange> range);

    Optional<Notification> findById(long id);

    int deleteOlderThan(LocalDateTime cutoff);

    void deleteByRecipient(long recipientId);
}
