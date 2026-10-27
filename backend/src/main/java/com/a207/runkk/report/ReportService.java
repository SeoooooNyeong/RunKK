package com.a207.runkk.report;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콘텐츠 신고 유스케이스를 조정한다(설계 6.1.12a, 6.2 A-24, FR-214~FR-216, D-104, D-115, D-138, D-149). 잠금 순서는 어느
 * 경로든 report_case 행 → 콘텐츠 행(crew_post·crew_chat_message)이라 서로 다른 신고자 셋이 동시에 신고해도 자동 숨김 전이와 hide 호출은
 * 한 번이다. 같은 이름의 analysis.ReportService(러닝 리포트)와 빈 이름이 겹치지 않게 빈 이름을 따로 준다.
 */
@Service("contentReportService")
public class ReportService {

    /** 운영자 처리 결과 알림 내용(FR-216). 이동은 사례 크루의 크루 홈이다(D-149 (4)). */
    static final String RESOLVED_TEXT = "신고하신 내용을 운영자가 처리했어요";

    /** 상태를 고르지 않은 관리툴 목록: 처리 전 사례. */
    private static final Collection<ReportStatus> OPEN =
            EnumSet.of(ReportStatus.RECEIVED, ReportStatus.AUTO_HIDDEN);

    private final ReportCaseRepository cases;
    private final List<ReportedContentPort> ports;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final Clock clock;

    public ReportService(
            ReportCaseRepository cases,
            List<ReportedContentPort> ports,
            NotificationService notificationService,
            AuditService auditService,
            Clock clock) {
        this.cases = cases;
        this.ports = List.copyOf(ports);
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * POST /reports(FR-214, FR-215). 대상 없음·숨김 → REPORT_TARGET_NOT_FOUND, 크루 밖 → NOT_CREW_MEMBER, 본인
     * 콘텐츠 → TARGET_INVALID → report_case 행 잠금(없으면 만들고 다시 읽어 잠금) → 잠근 사례가 AUTO_HIDDEN·HIDDEN이면(잠금을
     * 기다리는 사이 숨겨짐) REPORT_TARGET_NOT_FOUND → 같은 신고자의 신고가 있으면(기각된 것 포함) ALREADY_REPORTED →
     * DISMISSED면 reopen → 신고 저장 → addReport가 참이면 같은 트랜잭션에서 콘텐츠를 숨긴다.
     */
    @Transactional
    public ReportResult submit(long reporterId, ReportCommand command) {
        ReportTargetKind kind = command.targetKind();
        long targetId = command.targetId();
        ReportedContentPort port = portFor(kind);
        ReportedContent content =
                port.find(kind, targetId)
                        .filter(c -> !c.hidden())
                        .orElseThrow(
                                () ->
                                        new BusinessRuleException(
                                                ReasonCode.REPORT_TARGET_NOT_FOUND));
        if (!content.isCrewMember(reporterId)) {
            throw new BusinessRuleException(ReasonCode.NOT_CREW_MEMBER);
        }
        if (Objects.equals(content.authorId(), reporterId)) {
            throw new BusinessRuleException(ReasonCode.TARGET_INVALID);
        }
        LocalDateTime now = now();
        ReportCase c = lockCase(kind, targetId, content.crewId(), now);
        // 잠금 전에 읽은 콘텐츠는 그 사이 자동 숨김·운영자 숨김이 커밋됐을 수 있다. 잠근 사례가 숨김 상태면 숨긴 콘텐츠와 같이 거부한다
        // (처리된 사례에 닫히지 않는 열린 신고가 남지 않게, java-reviewer H1)
        if (c.getStatus() == ReportStatus.AUTO_HIDDEN || c.getStatus() == ReportStatus.HIDDEN) {
            throw new BusinessRuleException(ReasonCode.REPORT_TARGET_NOT_FOUND);
        }
        if (cases.existsReport(c.getId(), reporterId)) {
            throw new BusinessRuleException(ReasonCode.ALREADY_REPORTED);
        }
        if (c.getStatus() == ReportStatus.DISMISSED) {
            c.reopen(now);
        }
        ContentReport report =
                cases.saveReport(
                        new ContentReport(
                                c.getId(),
                                reporterId,
                                new ContentReport.Content(command.reason(), command.detail()),
                                now));
        if (c.addReport()) {
            port.hide(kind, targetId);
        }
        cases.saveCase(c);
        return new ReportResult(report.getId(), c.getStatus());
    }

    /** 대상의 사례를 잠가 읽는다. 없으면 넣고(동시에 넣은 쪽이 있으면 아무것도 하지 않는다) 다시 읽어 잠근다(6.2 A-24). */
    private ReportCase lockCase(
            ReportTargetKind kind, long targetId, long crewId, LocalDateTime now) {
        Optional<ReportCase> locked = cases.findByTargetForUpdate(kind, targetId);
        if (locked.isPresent()) {
            return locked.get();
        }
        cases.saveCase(ReportCase.open(kind, targetId, crewId, now));
        return cases.findByTargetForUpdate(kind, targetId)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "신고 사례를 만들지 못했다: " + kind + " " + targetId));
    }

    /**
     * GET /admin/reports(FR-216). status가 없으면 RECEIVED·AUTO_HIDDEN. openedAt 오래된 순이고, 미리보기는 포트
     * find로 채운다(숨긴 콘텐츠도 보인다, D-115). 대상이 지워졌으면 targetMissing.
     */
    @Transactional(readOnly = true)
    public ReportCaseListView cases(Optional<ReportStatus> status, PageCursor cursor) {
        Collection<ReportStatus> statuses =
                status.<Collection<ReportStatus>>map(EnumSet::of).orElse(OPEN);
        List<ReportCase> rows = cases.findByStatus(statuses, cursor);
        // 저장소가 커서를 이미 적용했으므로 크기만 자른다(nextCursor = 이 쪽 마지막 사례 ID)
        PageCursor.Slice<ReportCase> page =
                new PageCursor(null, cursor.size()).slice(rows, ReportCase::getId);
        return new ReportCaseListView(
                page.items().stream().map(this::view).toList(), page.nextCursor());
    }

    private ReportCaseView view(ReportCase c) {
        Optional<ReportedContent> content =
                portFor(c.getTargetKind()).find(c.getTargetKind(), c.getTargetId());
        Map<ReportReason, Integer> reasons = cases.reasonCounts(c.getId());
        ReportedPeriod period =
                cases.reportedPeriod(c.getId())
                        .orElse(new ReportedPeriod(c.getOpenedAt(), c.getOpenedAt()));
        return new ReportCaseView(
                c.getId(),
                c.getTargetKind(),
                c.getTargetId(),
                c.getCrewId(),
                content.map(ReportedContent::preview).orElse(null),
                content.isEmpty(),
                content.map(ReportedContent::authorId).orElse(null),
                c.getStatus(),
                reasons.values().stream().mapToInt(Integer::intValue).sum(),
                reasons,
                period.first(),
                period.last());
    }

    /**
     * POST /admin/reports/{caseId}/hide(FR-216, D-138). 사례 없음 → REPORT_NOT_FOUND, 이미 처리 →
     * REPORT_DECIDED. 자동 숨김이 아니었으면 콘텐츠를 숨긴다(자동 숨김이면 숨긴 채 둔다).
     *
     * @param operatorId web.admin OperatorCheckInterceptor가 확인한 운영자 계정(D-50)
     */
    @Transactional
    public ReportResolution hide(String operatorId, long caseId) {
        ReportCase c = lockCase(caseId);
        ReportStatus before = c.getStatus();
        c.hide(operatorId, now());
        if (before != ReportStatus.AUTO_HIDDEN) {
            portFor(c.getTargetKind()).hide(c.getTargetKind(), c.getTargetId());
        }
        return close(operatorId, c, before, AuditAction.REPORT_HIDE);
    }

    /**
     * POST /admin/reports/{caseId}/dismiss(FR-216, D-138). 사례 없음 → REPORT_NOT_FOUND, 이미 처리 →
     * REPORT_DECIDED. 숨김을 푼다 (자동 숨김 해제. 다시 보일 때 고정 공지가 이미 3개면 crew가 고정을 푼다, D-154).
     *
     * @param operatorId web.admin OperatorCheckInterceptor가 확인한 운영자 계정(D-50)
     */
    @Transactional
    public ReportResolution dismiss(String operatorId, long caseId) {
        ReportCase c = lockCase(caseId);
        ReportStatus before = c.getStatus();
        c.dismiss(operatorId, now());
        portFor(c.getTargetKind()).unhide(c.getTargetKind(), c.getTargetId());
        return close(operatorId, c, before, AuditAction.REPORT_DISMISS);
    }

    private ReportCase lockCase(long caseId) {
        return cases.findByIdForUpdate(caseId)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.REPORT_NOT_FOUND));
    }

    /**
     * 처리 공통 마무리: 열린 신고를 닫고, 감사(행위자 운영자 계정, 대상 caseId, 처리 전·뒤 상태)를 남기고, 열린 신고자에게 REPORT_RESOLVED (이동
     * = 사례 크루의 크루 홈). 알림함 행은 처리와 같은 트랜잭션에 쓰고 푸시는 NotificationService가 커밋 뒤에 보낸다.
     */
    private ReportResolution close(
            String operatorId, ReportCase c, ReportStatus before, AuditAction action) {
        List<Long> reporters = cases.openReporterIds(c.getId());
        cases.resolveOpenReports(c.getId());
        cases.saveCase(c);
        auditService.record(
                new AuditEntry(
                        operatorId,
                        ActorRole.OPERATOR,
                        action,
                        String.valueOf(c.getId()),
                        before.name(),
                        c.getStatus().name()));
        NavigationTarget target =
                new NavigationTarget(
                        NotificationType.REPORT_RESOLVED.defaultScreen(),
                        String.valueOf(c.getCrewId()));
        reporters.forEach(
                reporter ->
                        notificationService.notify(
                                new NotificationRequest(
                                        reporter,
                                        NotificationType.REPORT_RESOLVED,
                                        RESOLVED_TEXT,
                                        target)));
        return new ReportResolution(c.getId(), c.getStatus());
    }

    private ReportedContentPort portFor(ReportTargetKind kind) {
        return ports.stream()
                .filter(p -> p.supports(kind))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("신고 대상 포트가 없다: " + kind));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
