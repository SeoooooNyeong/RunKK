package com.a207.runkk.crew;

/**
 * 일정(크루런·대면 모임) 참석 응답(자료사전 참석응답: 참석 | 불참 | 미정, FR-145). v0.10 VoteAnswer를 개명하고 UNDECIDED를
 * 더했다(D-99). GOING이 크루런의 기존 "갈래요"(참가자)이고 UNDECIDED는 참가자가 아니다. DB 값(GOING·NOT_GOING)은 그대로다.
 */
public enum AttendanceAnswer {
    GOING,
    NOT_GOING,
    UNDECIDED
}
