package com.a207.runkk.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.test.util.ReflectionTestUtils;

/** 시험용 이벤트 저장소. ID를 매기고 포인트 지급 이력의 (회원, 이벤트, 대상) 고유 제약을 흉내 낸다. */
final class InMemoryEventRepository implements EventRepository {

    private final Map<Long, Event> rows = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final List<String> grants = new ArrayList<>();

    @Override
    public List<Event> findAll() {
        return List.copyOf(rows.values());
    }

    @Override
    public Optional<Event> findById(long id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public Optional<Event> findBySlug(String slug) {
        return rows.values().stream().filter(e -> e.getSlug().equals(slug)).findFirst();
    }

    @Override
    public Event save(Event event) {
        if (event.getId() == null) {
            ReflectionTestUtils.setField(event, "id", sequence.incrementAndGet());
        }
        rows.put(event.getId(), event);
        return event;
    }

    @Override
    public boolean pointGranted(long memberId, long eventId, String targetKey) {
        return grants.contains(memberId + "/" + eventId + "/" + targetKey);
    }

    @Override
    public void savePointGrant(EventPointGrant grant) {
        String key =
                ReflectionTestUtils.getField(grant, "memberId")
                        + "/"
                        + ReflectionTestUtils.getField(grant, "eventId")
                        + "/"
                        + ReflectionTestUtils.getField(grant, "targetKey");
        if (grants.contains(key)) {
            throw new org.springframework.dao.DataIntegrityViolationException(
                    "uq_event_point_grant");
        }
        grants.add(key);
    }

    Set<String> grants() {
        return Set.copyOf(grants);
    }
}
