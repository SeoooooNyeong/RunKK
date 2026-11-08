package com.a207.runkk.event;

import java.time.LocalDateTime;

/**
 * 관리 API 이벤트 한 건(S15P31A207-275). 비공개 이벤트도 보인다. 감사 기록의 변경 전후 값도 이 모양이다.
 *
 * @param id 이벤트 ID
 * @param slug 주소 이름
 * @param title 제목
 * @param subtitle 부제
 * @param kind 종류
 * @param config 종류별 설정 JSON 문자열
 * @param startsAt 시작 시각
 * @param endsAt 종료 시각
 * @param bannerImageUrl 배너 그림 주소
 * @param thumbnailImageUrl 썸네일 그림 주소
 * @param detailUrl 상세 웹 페이지 주소
 * @param sortOrder 정렬 순서
 * @param published 공개 여부
 * @param status 진행 상태(현재 시각 기준)
 */
public record EventAdminView(
        long id,
        String slug,
        String title,
        String subtitle,
        EventKind kind,
        String config,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String bannerImageUrl,
        String thumbnailImageUrl,
        String detailUrl,
        int sortOrder,
        boolean published,
        EventStatus status) {

    static EventAdminView of(Event e, LocalDateTime now) {
        return new EventAdminView(
                e.getId(),
                e.getSlug(),
                e.getTitle(),
                e.getSubtitle(),
                e.getKind(),
                e.getConfig(),
                e.getStartsAt(),
                e.getEndsAt(),
                e.getBannerImageUrl(),
                e.getThumbnailImageUrl(),
                e.getDetailUrl(),
                e.getSortOrder(),
                e.isPublished(),
                e.statusAt(now));
    }
}
