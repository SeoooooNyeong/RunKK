package com.a207.runkk.operator;

/**
 * 운영자 로그인 결과(설계 6.1.12 OperatorSession, D-42).
 *
 * @param adminToken 관리툴 토큰
 * @param actorId 운영자 계정 문자열
 */
public record OperatorSession(String adminToken, String actorId) {}
