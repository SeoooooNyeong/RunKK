package com.a207.runkk.crew;

/**
 * 게시 종류(자료사전 게시종류: 응원글 | 크루런 | 대면 모임 | 공지 | 투표 | 게시글, D-99). JPA 단일 테이블 상속의 구분 값(post_kind, 10자
 * 이하)과 같다. 일정은 CREW_RUN·MEETUP이다.
 */
public enum PostKind {
    CHEER,
    CREW_RUN,
    MEETUP,
    NOTICE,
    POLL,
    BOARD
}
