package com.a207.runkk.web.admin.event;

import com.a207.runkk.common.PageCursor;
import com.a207.runkk.event.EventAdminView;
import com.a207.runkk.event.EventEntrantService;
import com.a207.runkk.event.EventEntrants;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이벤트 관리(S15P31A207-275): 목록(비공개 포함), 생성, 수정, 공개 전환 → EventService, 응모자 명단 → EventEntrantService.
 * 운영자는 web.admin의 OperatorCheckInterceptor가 확인해 요청 속성 actorId로 붙인 계정이고 operator, web.admin을
 * import하지 않는다(D-50). 설정이 종류에 맞지 않거나 기간이 거꾸로이거나 slug가 겹치면 409 VALUE_INVALID, 없는 이벤트는 404
 * EVENT_NOT_FOUND.
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

    /** 응모자 명단의 size를 주지 않았을 때의 쪽 크기. 상한은 PageCursor.MAX_SIZE(100). */
    static final int DEFAULT_SIZE = 30;

    private final EventService eventService;
    private final EventEntrantService entrantService;

    public EventAdminController(EventService eventService, EventEntrantService entrantService) {
        this.eventService = eventService;
        this.entrantService = entrantService;
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
     * 응모자 명단(추첨용, 2026-11-08 사용자 결정). 응모 조건을 만족한 ACTIVE 회원의 회원 ID, 닉네임, 응모 항목을 회원 ID 내림차순 커서 쪽으로
     * 준다. celebrityId는 COACH_CELEB에서 그 셀럽 기록을 이긴 회원만 거른다. size가 없으면 30개, 1~100 밖이거나 거를 수 없는
     * celebrityId면 400, 없는 이벤트는 404 EVENT_NOT_FOUND.
     */
    @GetMapping("/{id}/entrants")
    EventEntrants entrants(
            @RequestAttribute(ACTOR_ID) String actorId,
            @PathVariable long id,
            @RequestParam(value = "celebrityId", required = false) Long celebrityId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        return entrantService.entrants(
                id, celebrityId, new PageCursor(cursor, size == null ? DEFAULT_SIZE : size));
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
