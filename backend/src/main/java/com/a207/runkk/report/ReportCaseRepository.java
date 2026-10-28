package com.a207.runkk.report;

import com.a207.runkk.common.PageCursor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 신고 저장 포트(설계 6.1.12a, D-104, D-149). 구현은 infra.report.JpaReportCaseRepository이고 report 밖에서 쓰지 않는다.
 */
public interface ReportCaseRepository {

    /** 대상의 사례를 행 잠금(SELECT … FOR UPDATE)으로 읽는다. 같은 대상의 신고는 여기서 줄을 선다(6.2 A-24). */
    Optional<ReportCase> findByTargetForUpdate(ReportTargetKind kind, long targetId);

    /**
     * 사례 저장. 새 사례(ID 없음)는 같은 대상이 없을 때만 넣고(INSERT … ON CONFLICT DO NOTHING, 트랜잭션을 깨지 않는다) 넘겨받은 값을
     * 그대로 돌려준다. 그래서 호출자는 넣은 뒤 언제나 findByTargetForUpdate로 다시 읽어 잠근다. 있는 사례는 바뀐 상태를 남긴다.
     */
    ReportCase saveCase(ReportCase c);

    /** 사례를 행 잠금으로 읽는다(운영자 처리). */
    Optional<ReportCase> findByIdForUpdate(long caseId);

    /**
     * 그 상태들의 사례를 openedAt 오래된 순(같으면 ID 순)으로, page.cursor 사례 다음부터 page.size + 1개((status, opened_at)
     * 색인). size가 없으면 전부.
     */
    List<ReportCase> findByStatus(Collection<ReportStatus> statuses, PageCursor page);

    /**
     * 신고 한 건을 바로 넣는다. 같은 사례·같은 신고자 고유 색인(uq_content_report_reporter) 위반은
     * BusinessRuleException(ALREADY_REPORTED)로 바꿔 던지고 다른 무결성 위반은 그대로 둔다(D-149).
     */
    ContentReport saveReport(ContentReport r);

    /** 그 사례에 그 신고자의 신고가 있는가(처리된 신고 포함, D-149). */
    boolean existsReport(long caseId, long reporterId);

    /** 열린(처리 전) 신고의 신고자 ID. 탈퇴로 익명화된 신고는 빠진다. */
    List<Long> openReporterIds(long caseId);

    /** 그 사례의 모든 신고의 사유별 수. */
    Map<ReportReason, Integer> reasonCounts(long caseId);

    /** 그 사례의 모든 신고의 첫·마지막 시각, 신고가 없으면 빈 값. */
    Optional<ReportedPeriod> reportedPeriod(long caseId);

    /** 그 사례의 열린 신고를 처리됨(resolved = true)으로. */
    void resolveOpenReports(long caseId);

    /** 그 회원의 모든 신고의 신고자를 지운다(탈퇴, FR-021). */
    void anonymizeReporter(long memberId);
}
