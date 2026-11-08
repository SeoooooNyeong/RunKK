package com.a207.runkk.web.admin.event;

import com.a207.runkk.event.EventAdminView;
import com.a207.runkk.event.EventInput;
import com.a207.runkk.event.EventKind;
import com.a207.runkk.event.EventService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이벤트 관리(S15P31A207-275): 목록(비공개 포함), 생성, 수정, 공개 전환 → EventService. 운영자는 web.admin의
 * OperatorCheckInterceptor가 확인해 요청 속성 actorId로 붙인 계정이고 operator, web.admin을 import하지 않는다(D-50). 설정이
 * 종류에 맞지 않거나 기간이 거꾸로이거나 slug가 겹치면 409 VALUE_INVALID, 없는 이벤트는 404 EVENT_NOT_FOUND.
 */
@RestController
@RequestMapping("/api/v1/admin/events")
public class EventAdminController {

    private static final String ACTOR_ID = "actorId";

    /**
     * 서버 기준 상대 경로만 받는다(이벤트 계약 2절). 앱은 "/"로 시작하는 경로만 API origin에 붙여 열므로(resolveEventPath) https 절대
     * 주소나 "//host" 주소를 받으면 저장은 되지만 앱에서 그림이 비고 카드가 열리지 않는다.
     */
    private static final String URL = "^/(?!/)[^\\s\\\\]*$";

    private final EventService eventService;

    public EventAdminController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    List<EventAdminView> list(@RequestAttribute(ACTOR_ID) String actorId) {
        return eventService.adminList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EventAdminView create(
            @RequestAttribute(ACTOR_ID) String actorId, @Valid @RequestBody EventRequest request) {
        return eventService.create(actorId, request.toInput());
    }

    @PutMapping("/{id}")
    EventAdminView update(
            @RequestAttribute(ACTOR_ID) String actorId,
            @PathVariable long id,
            @Valid @RequestBody EventRequest request) {
        return eventService.update(actorId, id, request.toInput());
    }

    @PutMapping("/{id}/published")
    EventAdminView changePublished(
            @RequestAttribute(ACTOR_ID) String actorId,
            @PathVariable long id,
            @Valid @RequestBody PublishedRequest request) {
        return eventService.changePublished(actorId, id, request.published());
    }

    /**
     * 이벤트 생성, 수정 입력.
     *
     * @param slug 주소 이름(영문 소문자, 숫자, -)
     * @param title 제목
     * @param subtitle 부제
     * @param kind 종류
     * @param config 종류별 설정. JSON 객체 그대로 또는 JSON 문자열
     * @param startsAt 시작 시각(한국 시간)
     * @param endsAt 종료 시각(한국 시간, 포함)
     * @param bannerImageUrl 배너 그림 주소(서버 기준 상대 경로, "/"로 시작하고 "//"로 시작하지 않음)
     * @param thumbnailImageUrl 썸네일 그림 주소
     * @param detailUrl 상세 웹 페이지 주소
     * @param sortOrder 같은 상태 안 정렬 순서
     * @param published 공개 여부
     */
    record EventRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[a-z0-9-]+$") String slug,
            @NotBlank @Size(max = 60) String title,
            @NotNull @Size(max = 100) String subtitle,
            @NotNull EventKind kind,
            @NotNull JsonNode config,
            @NotNull LocalDateTime startsAt,
            @NotNull LocalDateTime endsAt,
            @NotBlank @Size(max = 300) @Pattern(regexp = URL) String bannerImageUrl,
            @NotBlank @Size(max = 300) @Pattern(regexp = URL) String thumbnailImageUrl,
            @NotBlank @Size(max = 300) @Pattern(regexp = URL) String detailUrl,
            int sortOrder,
            boolean published) {

        EventInput toInput() {
            return new EventInput(
                    slug,
                    title,
                    subtitle,
                    kind,
                    config.isTextual() ? config.asText() : config.toString(),
                    startsAt,
                    endsAt,
                    bannerImageUrl,
                    thumbnailImageUrl,
                    detailUrl,
                    sortOrder,
                    published);
        }
    }

    /**
     * 공개 여부 바꾸기.
     *
     * @param published 공개 여부
     */
    record PublishedRequest(@NotNull Boolean published) {}
}
