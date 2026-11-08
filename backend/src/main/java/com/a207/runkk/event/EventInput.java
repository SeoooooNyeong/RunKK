package com.a207.runkk.event;

import java.time.LocalDateTime;

/**
 * 운영자 이벤트 생성·수정 입력(S15P31A207-275). 형식 검사는 web.admin.event가, 설정(config)과 기간·slug 중복 검사는
 * EventService가 한다.
 *
 * @param slug 주소 이름(영문 소문자·숫자·-)
 * @param title 제목
 * @param subtitle 부제
 * @param kind 종류
 * @param config 종류별 설정 JSON 문자열
 * @param startsAt 시작 시각(한국 시간)
 * @param endsAt 종료 시각(한국 시간, 이 시각까지 포함)
 * @param bannerImageUrl 배너 그림 주소(서버 기준 상대 경로)
 * @param thumbnailImageUrl 썸네일 그림 주소
 * @param detailUrl 상세 웹 페이지 주소
 * @param sortOrder 같은 상태 안 정렬 순서(오름차순)
 * @param published 공개 여부
 */
public record EventInput(
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
        boolean published) {}
