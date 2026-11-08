package com.a207.runkk.web.event;

import com.a207.runkk.event.EventEntry;
import com.a207.runkk.event.EventService;
import com.a207.runkk.event.EventView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 이벤트 목록과 내 참여(S15P31A207-275, GET /events, GET /events/{slug}/me → EventService). 다른 앱 API와 같은
 * 회원 인증이다. 없거나 비공개인 slug는 404 EVENT_NOT_FOUND. 상세 웹 페이지(detailUrl)는 nginx가 정적으로 준다.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private static final String MEMBER_ID = "memberId";

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    EventListResponse list(@RequestAttribute(MEMBER_ID) long memberId) {
        return new EventListResponse(eventService.list(memberId));
    }

    @GetMapping("/{slug}/me")
    EventEntry me(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable String slug) {
        return eventService.entry(memberId, slug);
    }

    /**
     * 이벤트 목록 응답.
     *
     * @param events 진행 중, 예정, 종료 순(같은 상태 안은 sortOrder 오름차순)
     */
    record EventListResponse(List<EventView> events) {}
}
