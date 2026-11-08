package com.a207.runkk.event;

import java.time.LocalDateTime;

/**
 * 앱 이벤트 목록 한 줄(GET /events, S15P31A207-275). 그림·상세 주소는 서버 주소 기준 상대 경로이고 앱이 API 주소의 origin에 붙인다.
 *
 * @param id 이벤트 ID
 * @param slug 주소 이름
 * @param title 제목
 * @param subtitle 부제
 * @param status 진행 상태(현재 시각 기준)
 * @param startsAt 시작 시각(한국 시간)
 * @param endsAt 종료 시각(한국 시간, 포함)
 * @param bannerImageUrl 배너 그림 주소
 * @param thumbnailImageUrl 썸네일 그림 주소
 * @param detailUrl 상세 웹 페이지 주소
 * @param entryCount 내 응모 수
 * @param maxEntryCount 최대 응모 수
 * @param sortOrder 같은 상태 안 정렬 순서
 */
public record EventView(
        long id,
        String slug,
        String title,
        String subtitle,
        EventStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String bannerImageUrl,
        String thumbnailImageUrl,
        String detailUrl,
        int entryCount,
        int maxEntryCount,
        int sortOrder) {}
