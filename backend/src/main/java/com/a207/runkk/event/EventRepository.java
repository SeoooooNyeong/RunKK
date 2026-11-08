package com.a207.runkk.event;

import java.util.List;
import java.util.Optional;

/** 이벤트 저장소 포트(S15P31A207-275). 구현은 infra.event.JpaEventRepository이고 event 밖에서 쓰지 않는다(13.1 규칙 4). */
public interface EventRepository {

    /** 공개 여부와 상관없이 전부. */
    List<Event> findAll();

    Optional<Event> findById(long id);

    Optional<Event> findBySlug(String slug);

    Event save(Event event);

    /** (회원, 이벤트, 대상) 포인트를 이미 줬는가. */
    boolean pointGranted(long memberId, long eventId, String targetKey);

    void savePointGrant(EventPointGrant grant);
}
