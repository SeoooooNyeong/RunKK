package com.a207.runkk.audit;

/**
 * 감사 대상 행위 14개(자료사전 행위종류 11개와 1:1, FR-176, + 운영자 로그인 성공·실패 2개, 코드 리뷰 2차 L25, + 운영자 랭킹 다시 계산 1개). 심박
 * 원자료 파기는 ROUTE_DISPOSAL의 before 값에 "HEART_RATE"를 적어 구분한다(제안, 부록 E E-09). 운영자 로그인 감사는 행위자 = 운영자 계정,
 * 대상 = 접속지, 변경 후 = 결과(SUCCESS 또는 실패 사유)다. 랭킹 다시 계산 감사는 행위자 = 운영자 계정, 대상 = "RANKING", 변경 후 = 집계
 * 시각·개인·크루 건수·저장 실패 수다. 자료사전 행위종류 추가 요청.
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
    SETTING_CHANGE,
    OPERATOR_LOGIN_SUCCESS,
    OPERATOR_LOGIN_FAILURE,
    RANKING_RECOMPUTED
}
