package com.a207.runkk.crew.chat;

/**
 * 채팅 조회 커서(설계 6.1.7a, 6.2 A-25, FR-209). before는 이 ID보다 오래된 것(위로 끌기), after는 이 ID보다 새 것(재연결 뒤
 * 메우기·폴링 모드), 둘 다 없으면 최신. 둘 다 주면 IllegalArgumentException(400). size는 0 이하면 50, 100을 넘으면 100으로
 * 줄인다(D-165).
 *
 * @param before 이 메시지 ID보다 오래된 것, 없으면 null
 * @param after 이 메시지 ID보다 새 것, 없으면 null
 * @param size 한 쪽 개수(1~100)
 */
public record ChatCursor(Long before, Long after, int size) {

    /** 기본 쪽 크기. */
    public static final int DEFAULT_SIZE = 50;

    /** 쪽 크기 상한. */
    public static final int MAX_SIZE = 100;

    public ChatCursor {
        if (before != null && after != null) {
            throw new IllegalArgumentException("before와 after를 함께 줄 수 없다");
        }
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }
    }

    /** 질의 값(size 없음 허용)으로 만든다. */
    public static ChatCursor of(Long before, Long after, Integer size) {
        return new ChatCursor(before, after, size == null ? DEFAULT_SIZE : size);
    }
}
