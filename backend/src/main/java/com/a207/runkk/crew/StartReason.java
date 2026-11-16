package com.a207.runkk.crew;

/** 출발 카운트다운을 연 계기(설계 05-32 4.1 start_reason, 자료사전 출발계기). */
public enum StartReason {
    /** 대기실 입장자 전원 준비(2명 이상). */
    ALL_READY,
    /** 모집자 「지금 시작」. */
    HOST,
    /** 예약 시각 자동 출발. */
    SCHEDULED
}
