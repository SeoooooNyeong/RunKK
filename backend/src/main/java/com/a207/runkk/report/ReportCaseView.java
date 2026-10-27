package com.a207.runkk.report;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 관리툴 신고 사례 한 줄(설계 6.1.12a, FR-216). 신고자 수·사유별 수·첫·마지막 신고 시각은 그 사례의 모든 신고(기각 전 신고, 탈퇴로 익명화된 신고 포함)로
 * 센다.
 *
 * @param caseId 사례 ID
 * @param targetKind 대상 종류
 * @param targetId 대상 ID
 * @param crewId 크루 ID
 * @param contentPreview 본문 앞 100자, 대상이 지워졌으면 null(숨긴 콘텐츠는 보인다, D-115)
 * @param targetMissing 대상이 지워짐
 * @param authorId 작성자 회원 ID, 탈퇴·대상 없음이면 null
 * @param status 처리 상태
 * @param reporterCount 신고자 수
 * @param reasons 사유별 신고 수
 * @param firstReportedAt 첫 신고 시각
 * @param lastReportedAt 마지막 신고 시각
 */
public record ReportCaseView(
        long caseId,
        ReportTargetKind targetKind,
        long targetId,
        long crewId,
        String contentPreview,
        boolean targetMissing,
        Long authorId,
        ReportStatus status,
        int reporterCount,
        Map<ReportReason, Integer> reasons,
        LocalDateTime firstReportedAt,
        LocalDateTime lastReportedAt) {

    public ReportCaseView {
        reasons = Map.copyOf(reasons);
    }
}
