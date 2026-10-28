package com.a207.runkk.report;

import java.time.LocalDateTime;

/**
 * 한 사례에 들어온 신고의 첫·마지막 시각(ReportCaseRepository.reportedPeriod 결과, 관리툴 목록의 firstReportedAt·
 * lastReportedAt).
 *
 * @param first 첫 신고 시각
 * @param last 마지막 신고 시각
 */
public record ReportedPeriod(LocalDateTime first, LocalDateTime last) {}
