package com.a207.runkk.runbody;

/**
 * 런바디 화면에 줄 세션 상태와 결과(설계 05-19 2.2, FR-266).
 *
 * @param recordId 기록 ID
 * @param status 작업 상태(DONE이 아니면 result는 null)
 * @param result ai 세션 결과 JSON(DONE일 때만)
 */
public record RunBodySessionView(String recordId, RunBodyStatus status, String result) {}
