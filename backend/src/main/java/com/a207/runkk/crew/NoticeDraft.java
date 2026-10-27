package com.a207.runkk.crew;

/**
 * 공지 입력(FR-205). 빈 본문은 IllegalArgumentException(400), 1,000자 초과는 NoticePost가 BODY_TOO_LONG(409)으로
 * 본다.
 *
 * @param body 본문(1~1,000자)
 * @param pinned 상단 고정, 기본 false
 */
public record NoticeDraft(String body, boolean pinned) {

    public NoticeDraft {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("공지 본문이 비었다");
        }
    }
}
