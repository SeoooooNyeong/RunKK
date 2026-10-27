package com.a207.runkk.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 신고 한 건의 신고자·사유(설계 6.1.12a, FR-214, D-104). 같은 신고자는 같은 사례에 한 번만(고유 색인 uq_content_report_reporter,
 * 신고자가 빈 익명화 행은 빼고). 신고자가 탈퇴하면 reporterId가 비고 행은 남는다(FR-021).
 */
@Entity
@Table(
        name = "content_report",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_content_report_reporter",
                        columnNames = {"case_id", "reporter_id"}))
public class ContentReport {

    /**
     * 신고내용 칼럼의 UTF-16 단위 상한. PostgreSQL VARCHAR(200)은 글자(코드 포인트)로 세지만 시험용 H2는 UTF-16 단위로 세므로 엔티티
     * 매핑은 두 배로 둔다(CrewChatMessage와 같은 처리). 실제 표는 V24가 만든다.
     */
    private static final int DETAIL_COLUMN_UNITS = ReportCommand.MAX_DETAIL * 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private long caseId;

    @Column(name = "reporter_id")
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 10)
    private ReportReason reason;

    @Column(name = "detail", updatable = false, length = DETAIL_COLUMN_UNITS)
    private String detail;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private LocalDateTime reportedAt;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    protected ContentReport() {}

    /**
     * 신고 한 건. 사유·내용 검사는 ReportCommand가 이미 했다(D-127).
     *
     * @param caseId 사례 ID
     * @param reporterId 신고자 회원 ID
     * @param content 사유와 신고내용
     * @param reportedAt 신고 시각
     */
    public ContentReport(long caseId, long reporterId, Content content, LocalDateTime reportedAt) {
        if (content == null || content.reason() == null || reportedAt == null) {
            throw new IllegalArgumentException("사유·신고 시각이 비었다");
        }
        this.caseId = caseId;
        this.reporterId = reporterId;
        this.reason = content.reason();
        this.detail = content.detail();
        this.reportedAt = reportedAt;
    }

    /**
     * 신고 사유와 내용.
     *
     * @param reason 사유
     * @param detail 신고내용, 없으면 null
     */
    public record Content(ReportReason reason, String detail) {}

    /** 신고자 익명화: 회원 탈퇴 때(FR-021). 사례와 신고 건수는 남긴다. */
    public void anonymizeReporter() {
        this.reporterId = null;
    }

    public Long getId() {
        return id;
    }

    public long getCaseId() {
        return caseId;
    }

    /** 신고자 회원 ID, 탈퇴했으면 null. */
    public Long getReporterId() {
        return reporterId;
    }

    public ReportReason getReason() {
        return reason;
    }

    /** 신고내용, 없으면 null. */
    public String getDetail() {
        return detail;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    /** 사례 처리(숨김·기각)로 닫힌 신고인가. */
    public boolean isResolved() {
        return resolved;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ContentReport r && id != null && Objects.equals(id, r.id);
    }

    @Override
    public int hashCode() {
        return ContentReport.class.hashCode();
    }
}
