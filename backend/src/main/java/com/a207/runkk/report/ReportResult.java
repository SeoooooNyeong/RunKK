package com.a207.runkk.report;

/**
 * 신고 접수 결과(설계 6.1.12a).
 *
 * @param reportId 신고 ID
 * @param status 접수 뒤 사례 상태(RECEIVED 또는 AUTO_HIDDEN)
 */
public record ReportResult(long reportId, ReportStatus status) {}
