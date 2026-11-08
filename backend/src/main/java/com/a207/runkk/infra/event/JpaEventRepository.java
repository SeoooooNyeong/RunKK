package com.a207.runkk.infra.event;

import com.a207.runkk.event.Event;
import com.a207.runkk.event.EventPointGrant;
import com.a207.runkk.event.EventRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * event.EventRepository의 JPA 구현(S15P31A207-275, V35_2). 이벤트는 몇 개뿐이라 목록은 전부 읽는다. 포인트 지급 이력은
 * uq_event_point_grant (member_id, event_id, target_key)로 확인한다.
 */
@Repository
public class JpaEventRepository implements EventRepository {

    private final EntityManager entityManager;

    public JpaEventRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Event> findAll() {
        return entityManager
                .createQuery("select e from Event e order by e.id", Event.class)
                .getResultList();
    }

    @Override
    public Optional<Event> findById(long id) {
        return Optional.ofNullable(entityManager.find(Event.class, id));
    }

    @Override
    public Optional<Event> findBySlug(String slug) {
        return entityManager
                .createQuery("select e from Event e where e.slug = :slug", Event.class)
                .setParameter("slug", slug)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public Event save(Event event) {
        if (event.getId() == null) {
            entityManager.persist(event);
            return event;
        }
        return entityManager.contains(event) ? event : entityManager.merge(event);
    }

    @Override
    public boolean pointGranted(long memberId, long eventId, String targetKey) {
        return !entityManager
                .createQuery(
                        "select g.id from EventPointGrant g where g.memberId = :m"
                                + " and g.eventId = :e and g.targetKey = :k",
                        Long.class)
                .setParameter("m", memberId)
                .setParameter("e", eventId)
                .setParameter("k", targetKey)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    @Override
    public void savePointGrant(EventPointGrant grant) {
        entityManager.persist(grant);
    }
}
