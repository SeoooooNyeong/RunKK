package com.a207.runkk.web.admin.report;

import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.ServerZone;
import com.a207.runkk.report.ReportCaseListView;
import com.a207.runkk.report.ReportCaseView;
import com.a207.runkk.report.ReportReason;
import com.a207.runkk.report.ReportResolution;
import com.a207.runkk.report.ReportService;
import com.a207.runkk.report.ReportStatus;
import com.a207.runkk.report.ReportTargetKind;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리툴 신고 처리(설계 5.7.1 GET /admin/reports → ReportService.cases, POST
 * /admin/reports/{caseId}/hide·dismiss → ReportService.hide·dismiss, FR-216, D-104, D-138). 운영자는
 * web.admin이 붙인 요청 속성 actorId로 받고 operator·web.admin을 import하지 않는다(D-50). 오류: 409
 * REPORT_NOT_FOUND·REPORT_DECIDED, 운영자가 아니면 403(OperatorCheckInterceptor).
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportAdminController {

    private static final String ACTOR_ID = "actorId";
    private static final ZoneId SEOUL = ServerZone.ID;

    /** size를 주지 않았을 때의 쪽 크기. 상한은 PageCursor.MAX_SIZE(100). */
    static final int DEFAULT_SIZE = 30;

    private final ReportService reportService;

    public ReportAdminController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 신고 사례 목록(status가 없으면 RECEIVED·AUTO_HIDDEN, openedAt 오래된 순). size가 없으면 30개, 1~100 밖이면 400. */
    @GetMapping
    ReportCaseListResponse cases(
            @RequestParam(value = "status", required = false) ReportStatus status,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        ReportCaseListView view =
                reportService.cases(
                        Optional.ofNullable(status),
                        new PageCursor(cursor, size == null ? DEFAULT_SIZE : size));
        return new ReportCaseListResponse(
                view.cases().stream().map(ReportAdminController::item).toList(), view.nextCursor());
    }

    /** 숨김(콘텐츠를 숨긴 채 둔다). */
    @PostMapping("/{caseId}/hide")
    ReportCaseStatusResponse hide(
            @RequestAttribute(ACTOR_ID) String actorId, @PathVariable("caseId") long caseId) {
        return status(reportService.hide(actorId, caseId));
    }

    /** 기각(자동 숨김을 푼다). */
    @PostMapping("/{caseId}/dismiss")
    ReportCaseStatusResponse dismiss(
            @RequestAttribute(ACTOR_ID) String actorId, @PathVariable("caseId") long caseId) {
        return status(reportService.dismiss(actorId, caseId));
    }

    private static ReportCaseStatusResponse status(ReportResolution resolution) {
        return new ReportCaseStatusResponse(resolution.caseId(), resolution.status());
    }

    private static ReportCaseItem item(ReportCaseView v) {
        return new ReportCaseItem(
                v.caseId(),
                v.targetKind(),
                v.targetId(),
                v.crewId(),
                v.contentPreview(),
                v.targetMissing(),
                v.authorId(),
                v.status(),
                v.reporterCount(),
                v.reasons(),
                at(v.firstReportedAt()),
                at(v.lastReportedAt()));
    }

    private static OffsetDateTime at(LocalDateTime time) {
        return time == null ? null : time.atZone(SEOUL).toOffsetDateTime();
    }

    /**
     * 신고 사례 목록.
     *
     * @param cases 사례
     * @param nextCursor 다음 페이지 커서, 없으면 null
     */
    record ReportCaseListResponse(List<ReportCaseItem> cases, Long nextCursor) {}

    /**
     * 신고 사례 한 줄.
     *
     * @param caseId 사례 ID
     * @param targetKind 대상 종류
     * @param targetId 대상 ID
     * @param crewId 크루 ID
     * @param contentPreview 본문 앞 100자, 대상이 지워졌으면 null
     * @param targetMissing 대상이 지워짐
     * @param authorId 작성자 회원 ID, 탈퇴면 null
     * @param status 처리 상태
     * @param reporterCount 신고자 수
     * @param reasons 사유별 신고 수
     * @param firstReportedAt 첫 신고 일시
     * @param lastReportedAt 마지막 신고 일시
     */
    record ReportCaseItem(
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
            OffsetDateTime firstReportedAt,
            OffsetDateTime lastReportedAt) {}

    /**
     * 숨김·기각 결과.
     *
     * @param caseId 사례 ID
     * @param status 처리 뒤 상태(HIDDEN 또는 DISMISSED)
     */
    record ReportCaseStatusResponse(long caseId, ReportStatus status) {}
}
