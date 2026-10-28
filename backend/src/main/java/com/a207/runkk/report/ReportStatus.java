package com.a207.runkk.report;

/** 신고 처리 상태(자료사전 신고처리상태, FR-215, FR-216). 운영자 처리 입력은 hide·dismiss 두 연산이다(D-138). */
public enum ReportStatus {
    /** 접수. */
    RECEIVED,
    /** 서로 다른 신고자 3명으로 자동 숨김. */
    AUTO_HIDDEN,
    /** 운영자 숨김. */
    HIDDEN,
    /** 운영자 기각. */
    DISMISSED
}
