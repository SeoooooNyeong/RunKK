package com.a207.runkk.audit;

/**
 * 감사 대상 행위 11개(자료사전 행위종류와 1:1, FR-176). 심박 원자료 파기는 ROUTE_DISPOSAL의 before 값에 "HEART_RATE"를 적어
 * 구분한다(제안, 부록 E E-09).
 */
public enum AuditAction {
    SIGN_UP_CONSENT,
    CONSENT_CHANGE,
    ROUTE_DISPOSAL,
    WITHDRAWAL,
    LEADER_TRANSFER,
    CREW_DELETE,
    KICK,
    POST_FORCE_DELETE,
    CELEBRITY_REGISTER,
    CELEBRITY_DELETE,
    SETTING_CHANGE
}
