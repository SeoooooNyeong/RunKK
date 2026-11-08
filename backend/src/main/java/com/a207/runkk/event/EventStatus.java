package com.a207.runkk.event;

import java.time.LocalDateTime;

/** 이벤트 진행 상태(S15P31A207-275). 서버가 현재 시각으로 계산하고, 목록은 이 선언 순서(진행 중, 예정, 종료)로 정렬한다. */
public enum EventStatus {
    ONGOING,
    UPCOMING,
    ENDED;

    /** 시작 전이면 UPCOMING, 종료 시각이 지났으면 ENDED, 그 사이(양 끝 포함)는 ONGOING. */
    static EventStatus of(LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
        if (now.isBefore(startsAt)) {
            return UPCOMING;
        }
        return now.isAfter(endsAt) ? ENDED : ONGOING;
    }
}
