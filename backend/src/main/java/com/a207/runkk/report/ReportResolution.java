package com.a207.runkk.report;

/**
 * 운영자 처리 결과(설계 6.1.12a, D-138).
 *
 * @param caseId 사례 ID
 * @param status 처리 뒤 상태(HIDDEN 또는 DISMISSED)
 */
public record ReportResolution(long caseId, ReportStatus status) {}
