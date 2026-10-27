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

    /**
     * 묶음 알림 저장(v0.11 D-102, 리뷰 M4): 같은 수신자·종류·대상 ID(target.targetId)의 읽지 않은 행이 있으면 내용·발생 일시만 고쳐 쓰고,
     * 없으면 새 행을 넣는다. 같은 대상의 동시 호출도 읽지 않은 행은 하나다(V23 부분 유일 색인 + 충돌하면 고쳐 쓰기). 내용은 이미 저장 길이 안이고 대상 ID가
     * 있어야 한다.
     */
    void saveCollapsing(NotificationRequest req, LocalDateTime occurredAt);

    int deleteOlderThan(LocalDateTime cutoff);

    void deleteByRecipient(long recipientId);
}
