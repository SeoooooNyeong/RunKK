package com.a207.runkk.crew;

import java.time.LocalDateTime;

/**
 * 대면 모임 입력(FR-132). 장소명·정원 범위 위반은 생성자의 IllegalArgumentException → 400(D-155). 일시가 지금 이전인지는 업무 규칙이라
 * MeetupPost가 SCHEDULE_INVALID(409)로, 본문 길이는 CrewPost가 BODY_TOO_LONG(409)으로 본다.
 *
 * @param body 본문(0~200자), 비울 수 있다
 * @param scheduledAt 일정일시
 * @param placeName 장소명(1~50자, 코드 포인트)
 * @param capacity 정원(2~10), 비우면 크루 정원
 */
public record MeetupDraft(
        String body, LocalDateTime scheduledAt, String placeName, Integer capacity) {

    static final int MAX_PLACE_NAME = 50;
    static final int MIN_CAPACITY = 2;

    public MeetupDraft {
        if (scheduledAt == null) {
            throw new IllegalArgumentException("일정일시가 없다");
        }
        if (placeName == null
                || placeName.isBlank()
                || CrewPost.codePoints(placeName) > MAX_PLACE_NAME) {
            throw new IllegalArgumentException("장소명은 1~50자");
        }
        if (capacity != null && (capacity < MIN_CAPACITY || capacity > Crew.CAPACITY)) {
            throw new IllegalArgumentException("정원은 2~10");
        }
    }
}
