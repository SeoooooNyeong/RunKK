package com.a207.runkk.infra.notification;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.notification.Notification;
import com.a207.runkk.notification.NotificationRepository;
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
