package com.a207.runkk.event;

/** 이벤트 종류(S15P31A207-275). 종류별 설정은 event.config(JSON 문자열)에 둔다(EventConfig). */
public enum EventKind {
    /** 셀럽런에서 지정한 셀럽 기록을 이기면 셀럽마다 응모 1회, 처음 이기면 포인트. */
    COACH_CELEB,
    /** 기간 안 유효 기록 거리가 기준 이상이면 응모 1회. */
    RUN_DISTANCE
}
