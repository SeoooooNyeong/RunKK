package com.a207.runkk.infra.notification;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.notification.Notification;
import com.a207.runkk.notification.NotificationRepository;
import com.a207.runkk.notification.NotificationRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * notification.NotificationRepository의 JPA 구현(설계 5.1.1 infra.notification). notification 테이블만 읽고
 * 쓴다.
 */
@Repository
public class JpaNotificationRepository implements NotificationRepository {

    private final EntityManager entityManager;

    public JpaNotificationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Notification save(Notification notification) {
        if (entityManager.contains(notification)) {
            return notification;
        }
        entityManager.persist(notification);
        return notification;
    }

    @Override
    public List<Notification> findByRecipient(long recipientId, Optional<DateRange> range) {
        String jpql =
                "select n from Notification n where n.recipientId = :recipient"
                        + (range.isPresent()
                                ? " and n.occurredAt >= :from and n.occurredAt < :to"
                                : "")
                        + " order by n.occurredAt desc, n.id desc";
        TypedQuery<Notification> query =
                entityManager
                        .createQuery(jpql, Notification.class)
                        .setParameter("recipient", recipientId);
        range.ifPresent(
                r ->
                        query.setParameter("from", r.start().atStartOfDay())
                                .setParameter("to", r.end().plusDays(1).atStartOfDay()));
        return query.getResultList();
    }

    @Override
    public Optional<Notification> findById(long id) {
        return Optional.ofNullable(entityManager.find(Notification.class, id));
    }

    /**
     * 읽지 않은 같은 대상 행 고쳐 쓰기 → 없으면 INSERT … ON CONFLICT DO NOTHING → 넣지 못했으면(같은 순간 다른 요청이 넣음, 그 커밋을
     * 기다린 뒤) 그 행을 다시 고쳐 쓴다. 충돌을 예외로 받지 않으므로 트랜잭션이 깨지지 않는다(리뷰 M4, V23
     * uq_notification_unread_collapse).
     */
    @Override
    public void saveCollapsing(NotificationRequest req, LocalDateTime occurredAt) {
        if (rewriteUnread(req, occurredAt) > 0) {
            return;
        }
        int inserted =
                entityManager
                        .createNativeQuery(
                                "insert into notification (recipient_id, type, content,"
                                    + " occurred_at, is_read, target_screen, target_id) values"
                                    + " (:recipient, :type, :content, :at, false, :screen, :target)"
                                    + " on conflict do nothing")
                        .setParameter("recipient", req.recipientId())
                        .setParameter("type", req.type().name())
                        .setParameter("content", req.content())
                        .setParameter("at", occurredAt)
                        .setParameter("screen", req.target().screen().name())
                        .setParameter("target", req.target().targetId())
                        .executeUpdate();
        if (inserted == 0) {
            rewriteUnread(req, occurredAt);
        }
    }

    private int rewriteUnread(NotificationRequest req, LocalDateTime occurredAt) {
        return entityManager
                .createQuery(
                        "update Notification n set n.content = :content, n.occurredAt = :at"
                                + " where n.recipientId = :recipient and n.type = :type"
                                + " and n.targetId = :target and n.read = false")
                .setParameter("content", req.content())
                .setParameter("at", occurredAt)
                .setParameter("recipient", req.recipientId())
                .setParameter("type", req.type())
                .setParameter("target", req.target().targetId())
                .executeUpdate();
    }

    @Override
    public int deleteOlderThan(LocalDateTime cutoff) {
        return entityManager
                .createQuery("delete from Notification n where n.occurredAt < :cutoff")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }

    @Override
    public void deleteByRecipient(long recipientId) {
        entityManager
                .createQuery("delete from Notification n where n.recipientId = :recipient")
                .setParameter("recipient", recipientId)
                .executeUpdate();
    }
}
