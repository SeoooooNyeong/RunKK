package com.a207.runkk.report;

/** 신고 사유(자료사전 신고사유, FR-214). OTHER면 신고내용이 필수다(D-149). */
public enum ReportReason {
    /** 욕설·비방. */
    ABUSE,
    /** 음란. */
    SEXUAL,
    /** 광고·스팸. */
    SPAM,
    /** 개인정보 노출. */
    PRIVACY,
    /** 기타. */
    OTHER
}
